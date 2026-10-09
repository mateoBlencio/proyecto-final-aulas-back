package ar.edu.utn.frc.siga.audit.repository;

import ar.edu.utn.frc.siga.AbstractIntegrationTest;
import ar.edu.utn.frc.siga.audit.model.ActorType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

/**
 * {@code findOperationChain} against real Postgres. The operations are inserted with {@code JdbcTemplate}
 * (one revision plus one row of {@code configuracion_aud} each), because the query only returns operations
 * that have audit rows.
 */
@DisplayName("AuditLogQueryRepository.findOperationChain (integración)")
class AuditLogQueryRepositoryChainIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private AuditLogQueryRepository repository;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    private final List<String> insertedOperations = new ArrayList<>();
    private final List<Integer> insertedRevisions = new ArrayList<>();

    @AfterEach
    void cleanUp() {
        insertedRevisions.forEach(rev -> {
            jdbcTemplate.update("DELETE FROM revinfo_resumen WHERE rev = ?", rev);
            jdbcTemplate.update("DELETE FROM configuracion_aud WHERE rev = ?", rev);
            jdbcTemplate.update("DELETE FROM revinfo WHERE rev = ?", rev);
        });
        insertedRevisions.clear();
        insertedOperations.clear();
    }

    private String newId() {
        return UUID.randomUUID().toString();
    }

    /** Inserts one revision (and one audit row) of {@code operationId}; the actor is SYSTEM when it has a parent. */
    private int revision(String operationId, String parentId) {
        Integer rev = jdbcTemplate.queryForObject(
                "INSERT INTO revinfo (fecha_revision, usuario, tipo_actor, descripcion, operacion_id, operacion_padre_id) "
                        + "VALUES (now(), 'chain-test', ?, ?, ?, ?) RETURNING rev",
                Integer.class, parentId == null ? "HUMAN" : "SYSTEM", "Op " + operationId, operationId, parentId);
        jdbcTemplate.update("INSERT INTO configuracion_aud (clave, rev, revtype, valor) VALUES (?, ?, 1, 'x')",
                "chain-test-" + UUID.randomUUID(), rev);
        jdbcTemplate.update("INSERT INTO revinfo_resumen (rev, tabla_aud, revtype, cantidad) VALUES (?, 'configuracion_aud', 1, 1)", rev);
        insertedRevisions.add(rev);
        insertedOperations.add(operationId);
        return rev;
    }

    private List<String> chainOf(String operationId) {
        return repository.findOperationChain(operationId).rows().stream().map(AuditGroupRow::operationId).toList();
    }

    @Test
    @DisplayName("desde cualquier nodo del árbol devuelve la raíz, sus hijos, hermanos y nietos, y no otro árbol")
    void returnsWholeTreeFromAnyNode() {
        String parent = newId();
        String child1 = newId();
        String child2 = newId();
        String grandchild = newId();
        String otherRoot = newId();
        String otherChild = newId();
        revision(parent, null);
        revision(child1, parent);
        revision(child2, parent);
        revision(grandchild, child1);
        revision(otherRoot, null);
        revision(otherChild, otherRoot);

        for (String start : List.of(parent, child1, child2, grandchild)) {
            assertThat(chainOf(start))
                    .as("cadena pedida desde %s", start)
                    .containsExactlyInAnyOrder(parent, child1, child2, grandchild);
        }
        assertThat(chainOf(otherChild)).containsExactlyInAnyOrder(otherRoot, otherChild);
        assertThat(repository.findOperationChain(grandchild).truncated()).isFalse();
    }

    @Test
    @DisplayName("una raíz con 201 hijos devuelve exactamente 200 operaciones, con la raíz, y truncated true")
    void operationCapTruncates() {
        String root = newId();
        revision(root, null);
        for (int i = 0; i < 201; i++) {
            revision(newId(), root);
        }

        AuditChainRows chain = repository.findOperationChain(root);

        assertThat(chain.rows()).hasSize(200);
        assertThat(chain.rows()).extracting(AuditGroupRow::operationId).contains(root);
        assertThat(chain.truncated()).isTrue();
    }

    @Test
    @DisplayName("una raíz con exactamente 199 hijos (200 operaciones) no se trunca")
    void exactlyAtOperationCapIsNotTruncated() {
        String root = newId();
        revision(root, null);
        for (int i = 0; i < 199; i++) {
            revision(newId(), root);
        }

        AuditChainRows chain = repository.findOperationChain(root);

        assertThat(chain.rows()).hasSize(200);
        assertThat(chain.truncated()).isFalse();
    }

    @Test
    @DisplayName("cada fila trae su parentOperationId, el recuento de su operación y el actor")
    void rowsCarryParentCountAndActor() {
        String parent = newId();
        String child = newId();
        revision(parent, null);
        revision(child, parent);
        revision(child, parent);

        List<AuditGroupRow> rows = repository.findOperationChain(child).rows();

        AuditGroupRow parentRow = rows.stream().filter(r -> parent.equals(r.operationId())).findFirst().orElseThrow();
        AuditGroupRow childRow = rows.stream().filter(r -> child.equals(r.operationId())).findFirst().orElseThrow();
        assertThat(rows).hasSize(2);
        assertThat(parentRow.parentOperationId()).isNull();
        assertThat(parentRow.recordCount()).isEqualTo(1);
        assertThat(parentRow.actorType()).isEqualTo(ActorType.HUMAN);
        assertThat(childRow.parentOperationId()).isEqualTo(parent);
        assertThat(childRow.recordCount()).isEqualTo(2);
        assertThat(childRow.actorType()).isEqualTo(ActorType.SYSTEM);
    }

    @Test
    @DisplayName("una operación sin padre ni hijos devuelve solo a sí misma")
    void isolatedOperationReturnsItself() {
        String alone = newId();
        revision(alone, null);

        assertThat(chainOf(alone)).containsExactly(alone);
    }

    @Test
    @DisplayName("un id inexistente devuelve lista vacía")
    void unknownIdReturnsEmpty() {
        AuditChainRows chain = repository.findOperationChain(newId());

        assertThat(chain.rows()).isEmpty();
        assertThat(chain.truncated()).isFalse();
    }

    @Test
    @DisplayName("un hijo cuyo padre no tiene filas devuelve al hijo y a sus hermanos, sin el padre")
    void missingParentIsSkipped() {
        String missingParent = newId();
        String child = newId();
        String sibling = newId();
        revision(child, missingParent);
        revision(sibling, missingParent);

        List<AuditGroupRow> rows = repository.findOperationChain(child).rows();

        assertThat(rows).extracting(AuditGroupRow::operationId).containsExactlyInAnyOrder(child, sibling);
        assertThat(rows).extracting(AuditGroupRow::parentOperationId).containsOnly(missingParent);
    }

    @Test
    @DisplayName("tope de 10 niveles: desde la raíz de una cadena de 15 devuelve la raíz y 10 niveles más")
    void depthCapFromRoot() {
        List<String> chain = linearChain(15);

        assertThat(chainOf(chain.getFirst())).containsExactlyInAnyOrderElementsOf(chain.subList(0, 11));
        assertThat(repository.findOperationChain(chain.getFirst()).truncated()).isTrue();
    }

    @Test
    @DisplayName("tope de 10 niveles: desde la hoja de una cadena de 15 sube 10 niveles y devuelve desde ahí hasta la hoja")
    void depthCapFromLeaf() {
        List<String> chain = linearChain(15);

        assertThat(chainOf(chain.getLast())).containsExactlyInAnyOrderElementsOf(chain.subList(4, 15));
        assertThat(repository.findOperationChain(chain.getLast()).truncated()).isTrue();
    }

    @Test
    @DisplayName("una cadena de 11 operaciones (10 niveles) se devuelve completa")
    void chainOfExactlyTenLevelsIsComplete() {
        List<String> chain = linearChain(11);

        assertThat(chainOf(chain.getFirst())).containsExactlyInAnyOrderElementsOf(chain);
        assertThat(chainOf(chain.getLast())).containsExactlyInAnyOrderElementsOf(chain);
        assertThat(repository.findOperationChain(chain.getFirst()).truncated()).isFalse();
        assertThat(repository.findOperationChain(chain.getLast()).truncated()).isFalse();
    }

    @Test
    @DisplayName("una cadena de 12 operaciones deja afuera la última al pedirla desde la raíz")
    void chainOfElevenLevelsCutsTheLast() {
        List<String> chain = linearChain(12);

        assertThat(chainOf(chain.getFirst())).containsExactlyInAnyOrderElementsOf(chain.subList(0, 11));
        assertThat(repository.findOperationChain(chain.getFirst()).truncated()).isTrue();
    }

    @Test
    @DisplayName("un ciclo A padre de B y B padre de A termina y devuelve las dos operaciones")
    void cycleTerminates() {
        String a = newId();
        String b = newId();
        revision(a, b);
        revision(b, a);

        List<String> fromA = assertTimeoutPreemptively(Duration.ofSeconds(10), () -> chainOf(a));
        List<String> fromB = assertTimeoutPreemptively(Duration.ofSeconds(10), () -> chainOf(b));

        assertThat(fromA).containsExactlyInAnyOrder(a, b);
        assertThat(fromB).containsExactlyInAnyOrder(a, b);
    }

    @Test
    @DisplayName("una operación que es su propio padre termina y se devuelve una sola vez")
    void selfParentTerminates() {
        String a = newId();
        revision(a, a);

        List<String> chain = assertTimeoutPreemptively(Duration.ofSeconds(10), () -> chainOf(a));

        assertThat(chain).containsExactly(a);
    }

    /** Operations 0..size-1 where each one is the child of the previous one. */
    private List<String> linearChain(int size) {
        List<String> chain = new ArrayList<>();
        for (int i = 0; i < size; i++) {
            String id = newId();
            revision(id, i == 0 ? null : chain.get(i - 1));
            chain.add(id);
        }
        return chain;
    }
}
