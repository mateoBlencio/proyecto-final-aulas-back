package ar.edu.utn.frc.siga.audit.service.impl;

import ar.edu.utn.frc.siga.audit.AuditLabelProvider;
import ar.edu.utn.frc.siga.audit.AuditedRecord;
import ar.edu.utn.frc.siga.audit.repository.AuditRecordStateRepository.RecordRevision;
import ar.edu.utn.frc.siga.audit.repository.AuditRecordStateRepository.RecordStates;
import ar.edu.utn.frc.siga.audit.service.AuditedEntity;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("AuditLabelResolver")
class AuditLabelResolverTest {

    private static final AuditedEntity ALLOCATION =
            new AuditedEntity(String.class, "Allocation", "Asignación", "asignacion_aula_aud", "id_asignacion", Long.class, List.of());
    private static final AuditedEntity OCCURRENCE =
            new AuditedEntity(Integer.class, "Occurrence", "Ocurrencia", "ocurrencia_aud", "id_ocurrencia", Long.class, List.of());
    private static final AuditedEntity SETTING =
            new AuditedEntity(Long.class, "Setting", "Configuración", "configuracion_aud", "clave", String.class, List.of());

    private final PlatformTransactionManager transactionManager = mock(PlatformTransactionManager.class);

    private AuditLabelResolver resolverOf(AuditLabelProvider... providers) {
        return new AuditLabelResolver(List.of(providers), transactionManager);
    }

    private static AuditLabelProvider providerOf(Class<?> entityType) {
        AuditLabelProvider provider = mock(AuditLabelProvider.class);
        doReturn(entityType).when(provider).entityType();
        return provider;
    }

    private static RecordStates currentOnly(Map<String, Object> current) {
        return new RecordStates(current, nullState());
    }

    private static Map<String, Object> nullState() {
        Map<String, Object> state = new HashMap<>();
        state.put("name", null);
        return state;
    }

    @SuppressWarnings("unchecked")
    private static List<AuditedRecord> recordsPassedTo(AuditLabelProvider provider) {
        ArgumentCaptor<List<AuditedRecord>> captor = ArgumentCaptor.forClass(List.class);
        verify(provider, times(1)).labels(captor.capture());
        return captor.getValue();
    }

    @Test
    @DisplayName("llama una sola vez al proveedor aunque haya 20 registros, con los 20 registros en esa llamada")
    void resolve_twentyRecords_callsProviderOnce() {
        AuditLabelProvider provider = providerOf(String.class);
        Map<String, String> labels = new HashMap<>();
        Map<RecordRevision, RecordStates> states = new LinkedHashMap<>();
        for (int i = 1; i <= 20; i++) {
            states.put(new RecordRevision(ALLOCATION, String.valueOf(i), 100 + i), currentOnly(Map.of("name", "n" + i)));
            labels.put(String.valueOf(i), "label-" + i);
        }
        when(provider.labels(any())).thenReturn(labels);

        Map<RecordRevision, String> result = resolverOf(provider).resolve(states);

        assertThat(recordsPassedTo(provider)).hasSize(20);
        assertThat(result).hasSize(20);
        assertThat(result.get(new RecordRevision(ALLOCATION, "7", 107))).isEqualTo("label-7");
    }

    @Test
    @DisplayName("con dos entidades llama una vez a cada proveedor y cada uno recibe solo sus registros")
    void resolve_twoEntities_callsEachProviderOnceWithItsOwnRecords() {
        AuditLabelProvider allocations = providerOf(String.class);
        AuditLabelProvider occurrences = providerOf(Integer.class);
        when(allocations.labels(any())).thenReturn(Map.of("1", "A1", "2", "A2"));
        when(occurrences.labels(any())).thenReturn(Map.of("9", "O9"));
        Map<RecordRevision, RecordStates> states = Map.of(
                new RecordRevision(ALLOCATION, "1", 10), currentOnly(Map.of("name", "a")),
                new RecordRevision(ALLOCATION, "2", 11), currentOnly(Map.of("name", "b")),
                new RecordRevision(OCCURRENCE, "9", 12), currentOnly(Map.of("name", "c")));

        Map<RecordRevision, String> result = resolverOf(allocations, occurrences).resolve(states);

        assertThat(recordsPassedTo(allocations)).extracting(AuditedRecord::recordId).containsExactly("1", "2");
        assertThat(recordsPassedTo(occurrences)).extracting(AuditedRecord::recordId).containsExactly("9");
        assertThat(result).hasSize(3);
    }

    @Test
    @DisplayName("un proveedor que lanza deja sin etiqueta solo a sus registros y los demás se etiquetan")
    void resolve_failingProvider_leavesOnlyItsRecordsUnlabelled() {
        AuditLabelProvider failing = providerOf(String.class);
        AuditLabelProvider healthy = providerOf(Integer.class);
        when(failing.labels(any())).thenThrow(new IllegalStateException("boom"));
        when(healthy.labels(any())).thenReturn(Map.of("9", "O9"));
        RecordRevision failed = new RecordRevision(ALLOCATION, "1", 10);
        RecordRevision labelled = new RecordRevision(OCCURRENCE, "9", 12);
        Map<RecordRevision, RecordStates> states = Map.of(
                failed, currentOnly(Map.of("name", "a")),
                labelled, currentOnly(Map.of("name", "c")));

        Map<RecordRevision, String> result = resolverOf(failing, healthy).resolve(states);

        assertThat(result).containsOnlyKeys(labelled).containsEntry(labelled, "O9");
        assertThat(result).doesNotContainKey(failed);
    }

    @Test
    @DisplayName("un registro en varias revisiones se etiqueta con el estado de la revisión más reciente")
    void resolve_recordInSeveralRevisions_usesLatestRevisionState() {
        AuditLabelProvider provider = providerOf(String.class);
        when(provider.labels(any())).thenReturn(Map.of("5", "latest"));
        RecordRevision older = new RecordRevision(ALLOCATION, "5", 3);
        RecordRevision newer = new RecordRevision(ALLOCATION, "5", 7);
        Map<RecordRevision, RecordStates> states = new LinkedHashMap<>();
        states.put(newer, currentOnly(Map.of("name", "new-state")));
        states.put(older, currentOnly(Map.of("name", "old-state")));

        Map<RecordRevision, String> result = resolverOf(provider).resolve(states);

        List<AuditedRecord> passed = recordsPassedTo(provider);
        assertThat(passed).singleElement().satisfies(record -> {
            assertThat(record.recordId()).isEqualTo("5");
            assertThat(record.text("name")).isEqualTo("new-state");
        });
        assertThat(result).containsEntry(older, "latest").containsEntry(newer, "latest");
    }

    @Test
    @DisplayName("la revisión más reciente gana aunque las claves lleguen en orden inverso")
    void resolve_latestRevisionWinsRegardlessOfInsertionOrder() {
        AuditLabelProvider provider = providerOf(String.class);
        when(provider.labels(any())).thenReturn(Map.of());
        Map<RecordRevision, RecordStates> states = new LinkedHashMap<>();
        states.put(new RecordRevision(ALLOCATION, "5", 3), currentOnly(Map.of("name", "old-state")));
        states.put(new RecordRevision(ALLOCATION, "5", 7), currentOnly(Map.of("name", "new-state")));

        resolverOf(provider).resolve(states);

        assertThat(recordsPassedTo(provider)).singleElement()
                .satisfies(record -> assertThat(record.text("name")).isEqualTo("new-state"));
    }

    @Test
    @DisplayName("si current viene vacío (todo null) usa previous, que tiene el último estado de la baja")
    void resolve_emptyCurrent_usesPrevious() {
        AuditLabelProvider provider = providerOf(String.class);
        when(provider.labels(any())).thenReturn(Map.of("5", "deleted-label"));
        Map<String, Object> previous = new HashMap<>();
        previous.put("name", "last-state");
        RecordRevision key = new RecordRevision(ALLOCATION, "5", 9);

        Map<RecordRevision, String> result = resolverOf(provider)
                .resolve(Map.of(key, new RecordStates(nullState(), previous)));

        assertThat(recordsPassedTo(provider)).singleElement()
                .satisfies(record -> assertThat(record.text("name")).isEqualTo("last-state"));
        assertThat(result).containsEntry(key, "deleted-label");
    }

    @Test
    @DisplayName("si current tiene algún valor no nulo ignora previous")
    void resolve_nonEmptyCurrent_ignoresPrevious() {
        AuditLabelProvider provider = providerOf(String.class);
        when(provider.labels(any())).thenReturn(Map.of());
        Map<String, Object> current = new HashMap<>();
        current.put("name", null);
        current.put("other", 1L);
        Map<String, Object> previous = new HashMap<>();
        previous.put("name", "previous-state");

        resolverOf(provider)
                .resolve(Map.of(new RecordRevision(ALLOCATION, "5", 9), new RecordStates(current, previous)));

        assertThat(recordsPassedTo(provider)).singleElement().satisfies(record -> {
            assertThat(record.text("name")).isNull();
            assertThat(record.longValue("other")).isEqualTo(1L);
        });
    }

    @Test
    @DisplayName("una entidad sin proveedor no da error y no devuelve etiqueta")
    void resolve_entityWithoutProvider_returnsNoLabel() {
        AuditLabelProvider provider = providerOf(String.class);
        Map<RecordRevision, RecordStates> states = Map.of(
                new RecordRevision(SETTING, "events.hours.end", 4), currentOnly(Map.of("name", "x")));

        Map<RecordRevision, String> result = resolverOf(provider).resolve(states);

        assertThat(result).isEmpty();
        verify(provider, never()).labels(any());
    }

    @Test
    @DisplayName("sin proveedores registrados devuelve vacío")
    void resolve_noProviders_returnsEmpty() {
        Map<RecordRevision, RecordStates> states = Map.of(
                new RecordRevision(ALLOCATION, "1", 4), currentOnly(Map.of("name", "x")));

        assertThat(resolverOf().resolve(states)).isEmpty();
    }

    @Test
    @DisplayName("sin estados no llama a ningún proveedor")
    void resolve_noStates_callsNoProvider() {
        AuditLabelProvider provider = providerOf(String.class);

        assertThat(resolverOf(provider).resolve(Map.of())).isEmpty();

        verify(provider, never()).labels(any());
    }

    @Test
    @DisplayName("un registro que el proveedor omite del resultado queda sin etiqueta")
    void resolve_recordMissingFromProviderResult_hasNoLabel() {
        AuditLabelProvider provider = providerOf(String.class);
        when(provider.labels(any())).thenReturn(Map.of("1", "A1"));
        RecordRevision labelled = new RecordRevision(ALLOCATION, "1", 10);
        RecordRevision unlabelled = new RecordRevision(ALLOCATION, "2", 11);
        Map<RecordRevision, RecordStates> states = Map.of(
                labelled, currentOnly(Map.of("name", "a")), unlabelled, currentOnly(Map.of("name", "b")));

        Map<RecordRevision, String> result = resolverOf(provider).resolve(states);

        assertThat(result).containsOnlyKeys(labelled);
    }

    @Test
    @DisplayName("supports es true solo para las entidades con proveedor")
    void supports_onlyEntitiesWithProvider() {
        AuditLabelResolver resolver = resolverOf(providerOf(String.class));

        assertThat(resolver.supports(ALLOCATION)).isTrue();
        assertThat(resolver.supports(SETTING)).isFalse();
    }

    @Test
    @DisplayName("cada proveedor corre en su propia transacción REQUIRES_NEW de solo lectura")
    void resolve_runsEachProviderInItsOwnReadOnlyRequiresNewTransaction() {
        AuditLabelProvider allocations = providerOf(String.class);
        AuditLabelProvider occurrences = providerOf(Integer.class);
        when(allocations.labels(any())).thenReturn(Map.of());
        when(occurrences.labels(any())).thenReturn(Map.of());
        when(transactionManager.getTransaction(any())).thenReturn(mock(TransactionStatus.class));

        resolverOf(allocations, occurrences).resolve(Map.of(
                new RecordRevision(ALLOCATION, "1", 10), currentOnly(Map.of("name", "a")),
                new RecordRevision(OCCURRENCE, "9", 12), currentOnly(Map.of("name", "c"))));

        ArgumentCaptor<TransactionDefinition> definitions = ArgumentCaptor.forClass(TransactionDefinition.class);
        verify(transactionManager, times(2)).getTransaction(definitions.capture());
        assertThat(definitions.getAllValues()).allSatisfy(definition -> {
            assertThat(definition.getPropagationBehavior()).isEqualTo(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
            assertThat(definition.isReadOnly()).isTrue();
        });
    }

    @Test
    @DisplayName("si el proveedor lanza, su transacción hace rollback y no commit")
    void resolve_failingProvider_rollsBackItsTransaction() {
        AuditLabelProvider failing = providerOf(String.class);
        when(failing.labels(any())).thenThrow(new IllegalStateException("boom"));
        TransactionStatus status = mock(TransactionStatus.class);
        when(transactionManager.getTransaction(any())).thenReturn(status);

        resolverOf(failing).resolve(Map.of(
                new RecordRevision(ALLOCATION, "1", 10), currentOnly(Map.of("name", "a"))));

        verify(transactionManager).rollback(status);
        verify(transactionManager, never()).commit(any());
    }
}
