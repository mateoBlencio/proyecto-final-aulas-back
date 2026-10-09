package ar.edu.utn.frc.siga.audit.internal;

import ar.edu.utn.frc.auditfixtures.SummaryEntityFixtures.ChildOfRootWithoutTable;
import ar.edu.utn.frc.auditfixtures.SummaryEntityFixtures.JoinedChild;
import ar.edu.utn.frc.auditfixtures.SummaryEntityFixtures.JoinedGrandChild;
import ar.edu.utn.frc.auditfixtures.SummaryEntityFixtures.Root;
import ar.edu.utn.frc.auditfixtures.SummaryEntityFixtures.RootWithBlankTableName;
import ar.edu.utn.frc.auditfixtures.SummaryEntityFixtures.RootWithoutTable;
import ar.edu.utn.frc.siga.audit.model.RevisionSummaryKey;
import ar.edu.utn.frc.siga.audit.model.SigaRevision;
import ar.edu.utn.frc.siga.events.model.AcademicEvent;
import ar.edu.utn.frc.siga.events.model.Occurrence;
import ar.edu.utn.frc.siga.events.model.RecurringEvent;
import ar.edu.utn.frc.siga.events.model.UniqueEvent;
import org.hibernate.envers.RevisionType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("SigaRevisionListener.entityChanged (resumen por revisión)")
class SigaRevisionListenerSummaryTest {

    private final SigaRevisionListener listener = new SigaRevisionListener();

    private SigaRevision change(Class<?> type, RevisionType revisionType) {
        SigaRevision revision = new SigaRevision();
        record(revision, type, revisionType);
        return revision;
    }

    private void record(SigaRevision revision, Class<?> type, RevisionType revisionType) {
        listener.entityChanged(type, type.getName(), 1L, revisionType, revision);
    }

    private static RevisionSummaryKey key(String auditTable, RevisionType revisionType) {
        return new RevisionSummaryKey(auditTable, revisionType.getRepresentation());
    }

    @Test
    @DisplayName("una entidad raíz cuenta en su tabla con el sufijo _aud")
    void rootEntity_countsInItsOwnAuditTable() {
        SigaRevision revision = change(Root.class, RevisionType.ADD);

        assertThat(revision.getSummary()).containsOnly(Map.entry(key("unit_root_aud", RevisionType.ADD), 1));
    }

    @Test
    @DisplayName("una subclase JOINED cuenta en la tabla de la raíz, no en la suya")
    void joinedSubclass_countsInRootTable() {
        SigaRevision revision = change(JoinedChild.class, RevisionType.MOD);

        assertThat(revision.getSummary()).containsOnly(Map.entry(key("unit_root_aud", RevisionType.MOD), 1));
    }

    @Test
    @DisplayName("una subclase de varios niveles y sin @Table propio sube hasta la raíz")
    void deepSubclassWithoutTable_countsInRootTable() {
        SigaRevision revision = change(JoinedGrandChild.class, RevisionType.DEL);

        assertThat(revision.getSummary()).containsOnly(Map.entry(key("unit_root_aud", RevisionType.DEL), 1));
    }

    @Test
    @DisplayName("la raíz y sus subclases se suman en la misma clave, sin duplicar tablas")
    void rootAndSubclasses_shareOneKey() {
        SigaRevision revision = new SigaRevision();

        record(revision, Root.class, RevisionType.ADD);
        record(revision, JoinedChild.class, RevisionType.ADD);
        record(revision, JoinedGrandChild.class, RevisionType.ADD);

        assertThat(revision.getSummary()).containsOnly(Map.entry(key("unit_root_aud", RevisionType.ADD), 3));
    }

    @Test
    @DisplayName("cada tipo de revisión tiene su propia clave: ADD, MOD y DEL de la misma tabla no se mezclan")
    void eachRevisionType_hasItsOwnKey() {
        SigaRevision revision = new SigaRevision();

        record(revision, Root.class, RevisionType.ADD);
        record(revision, Root.class, RevisionType.MOD);
        record(revision, Root.class, RevisionType.MOD);
        record(revision, Root.class, RevisionType.DEL);

        assertThat(revision.getSummary()).containsOnly(
                Map.entry(key("unit_root_aud", RevisionType.ADD), 1),
                Map.entry(key("unit_root_aud", RevisionType.MOD), 2),
                Map.entry(key("unit_root_aud", RevisionType.DEL), 1));
    }

    @Test
    @DisplayName("revisiones distintas llevan resúmenes independientes")
    void differentRevisions_haveIndependentSummaries() {
        SigaRevision first = change(Root.class, RevisionType.ADD);
        SigaRevision second = change(Root.class, RevisionType.ADD);

        assertThat(first.getSummary()).isNotSameAs(second.getSummary());
        assertThat(second.getSummary()).containsOnly(Map.entry(key("unit_root_aud", RevisionType.ADD), 1));
    }

    @Test
    @DisplayName("los tipos reales del repo resuelven a la tabla raíz: Occurrence, y RecurringEvent/UniqueEvent a evento_academico_aud")
    void realEntities_resolveToRootTables() {
        SigaRevision revision = new SigaRevision();

        record(revision, Occurrence.class, RevisionType.ADD);
        record(revision, AcademicEvent.class, RevisionType.ADD);
        record(revision, RecurringEvent.class, RevisionType.ADD);
        record(revision, UniqueEvent.class, RevisionType.ADD);

        assertThat(revision.getSummary()).containsOnly(
                Map.entry(key("ocurrencia_aud", RevisionType.ADD), 1),
                Map.entry(key("evento_academico_aud", RevisionType.ADD), 3));
    }

    @Test
    @DisplayName("una raíz sin @Table lanza IllegalStateException con el nombre de la clase, aunque la subclase tenga @Table")
    void rootWithoutTable_throws() {
        assertThatThrownBy(() -> change(RootWithoutTable.class, RevisionType.ADD))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(RootWithoutTable.class.getName())
                .hasMessageContaining("@Table(name)");
        assertThatThrownBy(() -> change(ChildOfRootWithoutTable.class, RevisionType.ADD))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(ChildOfRootWithoutTable.class.getName());
    }

    @Test
    @DisplayName("una raíz con @Table sin name lanza IllegalStateException")
    void rootWithBlankTableName_throws() {
        assertThatThrownBy(() -> change(RootWithBlankTableName.class, RevisionType.ADD))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(RootWithBlankTableName.class.getName());
    }

    @Test
    @DisplayName("el error por falta de @Table no queda cacheado: el segundo intento vuelve a lanzar")
    void missingTableError_isNotCached() {
        assertThatThrownBy(() -> change(RootWithoutTable.class, RevisionType.ADD))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> change(RootWithoutTable.class, RevisionType.MOD))
                .isInstanceOf(IllegalStateException.class);
    }
}
