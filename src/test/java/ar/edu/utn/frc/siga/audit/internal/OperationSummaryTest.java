package ar.edu.utn.frc.siga.audit.internal;

import ar.edu.utn.frc.siga.audit.dto.RevisionMetadata;
import ar.edu.utn.frc.siga.audit.repository.AuditChangeRow;
import ar.edu.utn.frc.siga.audit.repository.AuditLogQueryRepository;
import ar.edu.utn.frc.siga.audit.repository.AuditRecordStateRepository;
import ar.edu.utn.frc.siga.audit.repository.AuditRecordStateRepository.RecordRevision;
import ar.edu.utn.frc.siga.audit.repository.ChangeScope;
import ar.edu.utn.frc.siga.audit.service.AuditedEntity;
import ar.edu.utn.frc.siga.audit.service.AuditedEntityRegistry;
import ar.edu.utn.frc.siga.audit.service.impl.AuditLabelResolver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.Pageable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("OperationSummary")
class OperationSummaryTest {

    private static final String BASE = "Baja de materia";
    private static final AuditedEntity SUBJECT =
            new AuditedEntity(String.class, "Subject", "Materia", "materia_aud", "id_materia", Long.class, List.of());

    private final AuditedEntityRegistry registry = mock(AuditedEntityRegistry.class);
    private final AuditLogQueryRepository queryRepository = mock(AuditLogQueryRepository.class);
    private final AuditRecordStateRepository stateRepository = mock(AuditRecordStateRepository.class);
    private final AuditLabelResolver labelResolver = mock(AuditLabelResolver.class);

    private OperationSummary summary;

    @BeforeEach
    void setUp() {
        summary = new OperationSummary(registry, queryRepository, stateRepository, labelResolver);
    }

    private static List<String> names(int count) {
        return IntStream.rangeClosed(1, count).mapToObj(i -> "Materia " + (char) ('A' + i - 1)).toList();
    }

    private static AuditChangeRow row(int recordId) {
        return new AuditChangeRow(new RevisionMetadata(String.valueOf(recordId), 10, null, null, null, null, null, "op-1"),
                SUBJECT);
    }

    private void operationChanges(List<AuditChangeRow> rows, List<String> labels) {
        when(registry.bySimpleName("Subject")).thenReturn(Optional.of(SUBJECT));
        when(queryRepository.findChanges(any(), any(), any())).thenReturn(rows);
        Map<RecordRevision, String> resolved = new LinkedHashMap<>();
        for (int i = 0; i < labels.size(); i++) {
            resolved.put(new RecordRevision(SUBJECT, String.valueOf(i), 10), labels.get(i));
        }
        when(labelResolver.resolveSummaries(any())).thenReturn(resolved);
    }

    @Test
    @DisplayName("compose: sin nombres devuelve la base tal cual")
    void compose_noNames_returnsBase() {
        assertThat(OperationSummary.compose(BASE, List.of())).isEqualTo(BASE);
    }

    @Test
    @DisplayName("compose: 1 nombre es 'base: A'")
    void compose_oneName() {
        assertThat(OperationSummary.compose(BASE, List.of("Física I (1)"))).isEqualTo("Baja de materia: Física I (1)");
    }

    @Test
    @DisplayName("compose: 2 nombres se unen con ' y ', sin 'más'")
    void compose_twoNames() {
        assertThat(OperationSummary.compose(BASE, List.of("A", "B"))).isEqualTo("Baja de materia: A y B");
    }

    @Test
    @DisplayName("compose: 3 nombres se listan completos 'A, B y C'")
    void compose_threeNames_listedInFull() {
        assertThat(OperationSummary.compose(BASE, List.of("A", "B", "C"))).isEqualTo("Baja de materia: A, B y C");
    }

    @Test
    @DisplayName("compose: 4 nombres es el primer caso con 'más': 'A, B y 2 más'")
    void compose_fourNames_firstWithMore() {
        assertThat(OperationSummary.compose(BASE, List.of("A", "B", "C", "D"))).isEqualTo("Baja de materia: A, B y 2 más");
    }

    @Test
    @DisplayName("compose: 17 nombres dan 'A, B y 15 más'")
    void compose_seventeenNames() {
        assertThat(OperationSummary.compose(BASE, names(17))).isEqualTo("Baja de materia: Materia A, Materia B y 15 más");
    }

    @Test
    @DisplayName("compose: 3 nombres largos que pasan 255 sacan un nombre y suben el 'más' a 1")
    void compose_threeLongNames_dropsOneName() {
        String first = "a".repeat(100);
        String second = "b".repeat(100);
        String third = "c".repeat(100);

        String result = OperationSummary.compose(BASE, List.of(first, second, third));

        assertThat(result).isEqualTo(BASE + ": " + first + ", " + second + " y 1 más");
        assertThat(result.length()).isLessThanOrEqualTo(255);
    }

    @Test
    @DisplayName("compose: si ni dos nombres entran lista uno solo y sube el 'más'")
    void compose_twoDoNotFit_listsOne() {
        List<String> longNames = Collections.nCopies(17, "x".repeat(130));

        String result = OperationSummary.compose(BASE, longNames);

        assertThat(result).isEqualTo(BASE + ": " + "x".repeat(130) + " y 16 más");
    }

    @Test
    @DisplayName("compose: un texto de exactamente 255 caracteres no se recorta")
    void compose_exactly255_notTruncated() {
        String name = "n".repeat(255 - (BASE + ": ").length());

        String result = OperationSummary.compose(BASE, List.of(name));

        assertThat(result).hasSize(255).doesNotEndWith("…").isEqualTo(BASE + ": " + name);
    }

    @Test
    @DisplayName("compose: un nombre que pasa 255 por un carácter termina en '…' y mide 255")
    void compose_256_truncatedWithEllipsis() {
        String name = "n".repeat(256 - (BASE + ": ").length());

        String result = OperationSummary.compose(BASE, List.of(name));

        assertThat(result).hasSize(255).endsWith("…").startsWith(BASE + ": ");
    }

    @Test
    @DisplayName("compose: un nombre de 300 caracteres termina en '…' y mide 255")
    void compose_300CharName_truncatedTo255() {
        String result = OperationSummary.compose(BASE, List.of("n".repeat(300)));

        assertThat(result).hasSize(255).endsWith("…").startsWith(BASE + ": ");
    }

    @Test
    @DisplayName("compose: nunca pasa de 255 aunque haya muchos nombres de 300 caracteres")
    void compose_manyHugeNames_neverOver255() {
        List<String> huge = Collections.nCopies(40, "z".repeat(300));

        assertThat(OperationSummary.compose(BASE, huge).length()).isLessThanOrEqualTo(255);
    }

    @Test
    @DisplayName("describe: ordena los nombres con el orden del español (Á junto a A), sin repetidos")
    void describe_sortsSpanishAndDeduplicates() {
        operationChanges(List.of(row(1)), List.of("Zoología", "Álgebra", "Biología", "Álgebra"));

        assertThat(summary.describe("op-1", BASE, "Subject")).isEqualTo("Baja de materia: Álgebra, Biología y Zoología");
    }


    @Test
    @DisplayName("describe: la coma interna de un nombre ('Pérez, Juan') se reemplaza por un espacio para no confundirse con el separador")
    void describe_commaInsideName_replacedBySpace() {
        operationChanges(List.of(row(1)), List.of("Pérez, Juan"));

        assertThat(summary.describe("op-1", "Alta de docente", "Subject")).isEqualTo("Alta de docente: Pérez Juan");
    }

    @Test
    @DisplayName("describe: con dos docentes 'Apellido, Nombre' la lista queda 'A B y C D', sin comas dentro de los nombres")
    void describe_twoNamesWithCommas_listIsUnambiguous() {
        operationChanges(List.of(row(1), row(2)), List.of("Pérez, Juan", "Gómez ,Ana"));

        assertThat(summary.describe("op-1", "Alta de docente", "Subject"))
                .isEqualTo("Alta de docente: Gómez Ana y Pérez Juan");
    }

    @Test
    @DisplayName("describe: cuatro docentes con coma dan 'A B, C D y 2 más' (la única coma de la frase es el separador)")
    void describe_fourNamesWithCommas_oneSeparatorComma() {
        operationChanges(List.of(row(1)), List.of("Pérez, Juan", "Gómez, Ana", "Ríos, Luis", "Soto, Eva"));

        String result = summary.describe("op-1", "Alta de docente", "Subject");

        assertThat(result).isEqualTo("Alta de docente: Gómez Ana, Pérez Juan y 2 más");
        assertThat(result.chars().filter(c -> c == ',').count()).isEqualTo(1);
    }

    @Test
    @DisplayName("describe: 'Pérez, Juan' y 'Pérez Juan' son el mismo nombre una vez sin coma y se listan una sola vez")
    void describe_namesEqualWithoutComma_deduplicated() {
        operationChanges(List.of(row(1)), List.of("Pérez, Juan", "Pérez Juan"));

        assertThat(summary.describe("op-1", "Alta de docente", "Subject")).isEqualTo("Alta de docente: Pérez Juan");
    }

    @Test
    @DisplayName("describe: los paréntesis del nombre ('Aula 101 (Central)') se conservan")
    void describe_parenthesesInNameAreKept() {
        operationChanges(List.of(row(1), row(2)), List.of("Aula 101 (Central)", "Día de la Independencia (09/07/2026)"));

        assertThat(summary.describe("op-1", BASE, "Subject"))
                .isEqualTo("Baja de materia: Aula 101 (Central) y Día de la Independencia (09/07/2026)");
    }
    @Test
    @DisplayName("describe: consulta los cambios de la operación pedida sobre la entidad del subject")
    void describe_queriesOperationScopeOfSubjectEntity() {
        operationChanges(List.of(row(1)), List.of("Física I (1)"));

        summary.describe("op-1", BASE, "Subject");

        ArgumentCaptor<ChangeScope> scope = ArgumentCaptor.forClass(ChangeScope.class);
        verify(queryRepository).findChanges(any(), scope.capture(), any(Pageable.class));
        assertThat(scope.getValue().operationId()).isEqualTo("op-1");
    }

    @Test
    @DisplayName("describe: si el subject no es una entidad auditada devuelve la base y no consulta cambios")
    void describe_unknownSubject_returnsBase() {
        when(registry.bySimpleName("Inexistente")).thenReturn(Optional.empty());

        assertThat(summary.describe("op-1", BASE, "Inexistente")).isEqualTo(BASE);
        verify(queryRepository, never()).findChanges(any(), any(), any());
    }

    @Test
    @DisplayName("describe: si armar el resumen lanza, devuelve la base en vez de propagar")
    void describe_failure_returnsBase() {
        when(registry.bySimpleName("Subject")).thenReturn(Optional.of(SUBJECT));
        when(queryRepository.findChanges(any(), any(), any())).thenThrow(new IllegalStateException("boom"));

        assertThat(summary.describe("op-1", BASE, "Subject")).isEqualTo(BASE);
    }

    @Test
    @DisplayName("describe: sin registros con resumen devuelve la base")
    void describe_noNames_returnsBase() {
        operationChanges(List.of(), List.of());

        assertThat(summary.describe("op-1", BASE, "Subject")).isEqualTo(BASE);
    }

    @Test
    @DisplayName("describe: pagina de a 5000 filas hasta que una página viene incompleta")
    void describe_pagesUntilShortPage() {
        List<AuditChangeRow> fullPage = new ArrayList<>(Collections.nCopies(5000, row(1)));
        when(registry.bySimpleName("Subject")).thenReturn(Optional.of(SUBJECT));
        when(queryRepository.findChanges(any(), any(), any())).thenReturn(fullPage, List.of(row(2)));
        when(labelResolver.resolveSummaries(any())).thenReturn(Map.of(new RecordRevision(SUBJECT, "1", 10), "A"));

        summary.describe("op-1", BASE, "Subject");

        ArgumentCaptor<Pageable> pages = ArgumentCaptor.forClass(Pageable.class);
        verify(queryRepository, times(2)).findChanges(any(), any(), pages.capture());
        assertThat(pages.getAllValues()).extracting(Pageable::getPageNumber).containsExactly(0, 1);
        assertThat(pages.getAllValues()).extracting(Pageable::getPageSize).containsOnly(5000);
        verify(stateRepository, times(2)).load(any());
        verify(labelResolver, times(2)).resolveSummaries(any());
    }

    @Test
    @DisplayName("describe: con una página exacta de 5000 pide una segunda y termina cuando viene vacía")
    void describe_exactPage_requestsNextEmptyPage() {
        when(registry.bySimpleName("Subject")).thenReturn(Optional.of(SUBJECT));
        when(queryRepository.findChanges(any(), any(), any()))
                .thenReturn(new ArrayList<>(Collections.nCopies(5000, row(1))), List.of());
        when(labelResolver.resolveSummaries(any())).thenReturn(Map.of());

        assertThat(summary.describe("op-1", BASE, "Subject")).isEqualTo(BASE);
        verify(queryRepository, times(2)).findChanges(any(), any(), any());
    }
}
