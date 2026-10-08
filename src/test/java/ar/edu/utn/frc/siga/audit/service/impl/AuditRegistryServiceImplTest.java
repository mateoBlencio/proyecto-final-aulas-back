package ar.edu.utn.frc.siga.audit.service.impl;

import ar.edu.utn.frc.siga.audit.dto.AuditLogFilter;
import ar.edu.utn.frc.siga.audit.dto.RevisionMetadata;
import ar.edu.utn.frc.siga.audit.dto.response.AuditLogEntryDto;
import ar.edu.utn.frc.siga.audit.dto.response.AuditLogEntryType;
import ar.edu.utn.frc.siga.audit.mapper.AuditLogEntryMapperImpl;
import ar.edu.utn.frc.siga.audit.model.ActorType;
import ar.edu.utn.frc.siga.audit.model.RevisionKind;
import ar.edu.utn.frc.siga.audit.repository.AuditChangeRow;
import ar.edu.utn.frc.siga.audit.repository.AuditGroupRow;
import ar.edu.utn.frc.siga.audit.repository.AuditLogCriteria;
import ar.edu.utn.frc.siga.audit.repository.AuditLogQueryRepository;
import ar.edu.utn.frc.siga.audit.repository.AuditRecordStateRepository;
import ar.edu.utn.frc.siga.audit.repository.AuditRecordStateRepository.RecordRevision;
import ar.edu.utn.frc.siga.audit.repository.AuditRecordStateRepository.RecordStates;
import ar.edu.utn.frc.siga.audit.dto.response.FieldChangeDto;
import ar.edu.utn.frc.siga.audit.repository.ChangeScope;
import ar.edu.utn.frc.siga.audit.service.AuditedEntity;
import ar.edu.utn.frc.siga.audit.service.AuditedEntityRegistry;
import ar.edu.utn.frc.siga.common.exception.InvalidDateRangeException;
import ar.edu.utn.frc.siga.common.exception.InvalidSelectionException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("AuditRegistryServiceImpl")
class AuditRegistryServiceImplTest {

    private static final AuditedEntity ALLOCATION =
            new AuditedEntity(String.class, "Allocation", "Asignación", "asignacion_aula_aud", "id_asignacion", Long.class, List.of());
    private static final AuditedEntity SETTING =
            new AuditedEntity(Integer.class, "Setting", "Configuración", "configuracion_aud", "clave", String.class, List.of());
    private static final LocalDateTime DATE = LocalDateTime.of(2026, 5, 4, 10, 30);

    @Mock
    private AuditLogQueryRepository repository;
    @Mock
    private AuditRecordStateRepository stateRepository;
    @Mock
    private AuditedEntityRegistry registry;

    private AuditRegistryServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new AuditRegistryServiceImpl(repository, stateRepository, registry, new AuditLogEntryMapperImpl());
        lenient().when(registry.all()).thenReturn(List.of(ALLOCATION, SETTING));
        lenient().when(registry.byLabel("Asignación")).thenReturn(Optional.of(ALLOCATION));
        lenient().when(registry.byLabel("Configuración")).thenReturn(Optional.of(SETTING));
        lenient().when(registry.byLabel("NoExiste")).thenReturn(Optional.empty());
    }

    private static AuditGroupRow operationGroup(int revision, String operationId, String description,
                                                long recordCount) {
        return new AuditGroupRow(operationId, revision, DATE, "user@frc", ActorType.HUMAN, description, recordCount,
                List.of("Asignación"), RevisionKind.CREATED, "Asignación", "7");
    }

    private static AuditGroupRow looseGroup(int revision, long recordCount, List<String> entityTypes,
                                            RevisionKind commonKind, String description) {
        return new AuditGroupRow(null, revision, DATE, "user@frc", ActorType.HUMAN, description, recordCount, entityTypes,
                commonKind, entityTypes.getFirst(), "42");
    }

    private static AuditLogFilter filter(String entityType) {
        return new AuditLogFilter(null, null, null, entityType, null, null, null);
    }

    private AuditLogCriteria capturedGroupCriteria() {
        ArgumentCaptor<AuditLogCriteria> captor = ArgumentCaptor.forClass(AuditLogCriteria.class);
        verify(repository).findGroups(captor.capture(), any(Pageable.class));
        return captor.getValue();
    }

    @Test
    @DisplayName("fila con operationId -> OPERATION con operationId, recordCount y description de la fila")
    void findAll_rowWithOperationId_mapsToOperation() {
        when(repository.findGroups(any(), any())).thenReturn(
                List.of(operationGroup(11, "op-1", "Asignación de aulas en lote", 3)));
        when(repository.countGroups(any())).thenReturn(1L);

        Page<AuditLogEntryDto> page = service.findAll(filter(null), PageRequest.of(0, 10));

        AuditLogEntryDto operation = page.getContent().getFirst();
        assertThat(operation.type()).isEqualTo(AuditLogEntryType.OPERATION);
        assertThat(operation.operationId()).isEqualTo("op-1");
        assertThat(operation.description()).isEqualTo("Asignación de aulas en lote");
        assertThat(operation.recordCount()).isEqualTo(3);
        assertThat(operation.revision()).isEqualTo(11);
        assertThat(operation.entityTypes()).containsExactly("Asignación");
        assertThat(operation.entityType()).isNull();
        assertThat(operation.recordId()).isNull();
    }

    @Test
    @DisplayName("fila con operationId y recordCount 1 sigue siendo OPERATION")
    void findAll_operationWithSingleRecord_staysOperation() {
        when(repository.findGroups(any(), any())).thenReturn(List.of(operationGroup(11, "op-1", "Alta", 1)));
        when(repository.countGroups(any())).thenReturn(1L);

        Page<AuditLogEntryDto> page = service.findAll(filter(null), PageRequest.of(0, 10));

        assertThat(page.getContent().getFirst().type()).isEqualTo(AuditLogEntryType.OPERATION);
    }

    @Test
    @DisplayName("fila sin operación y recordCount 1 -> CHANGE con entityType/recordId de la fila y descripción templada")
    void findAll_looseSingleRecord_mapsToChangeWithTemplatedDescription() {
        when(repository.findGroups(any(), any())).thenReturn(
                List.of(looseGroup(5, 1, List.of("Asignación"), RevisionKind.CREATED, null)));
        when(repository.countGroups(any())).thenReturn(1L);

        Page<AuditLogEntryDto> page = service.findAll(filter(null), PageRequest.of(0, 10));

        AuditLogEntryDto change = page.getContent().getFirst();
        assertThat(change.type()).isEqualTo(AuditLogEntryType.CHANGE);
        assertThat(change.entityType()).isEqualTo("Asignación");
        assertThat(change.recordId()).isEqualTo("42");
        assertThat(change.kind()).isEqualTo(RevisionKind.CREATED);
        assertThat(change.operationId()).isNull();
        assertThat(change.revision()).isEqualTo(5);
        assertThat(change.description()).isEqualTo("Alta de Asignación");
    }

    @Test
    @DisplayName("fila sin operación y recordCount > 1 -> TRANSACTION sin operationId y con descripción de conteo")
    void findAll_looseMultipleRecords_mapsToTransaction() {
        when(repository.findGroups(any(), any())).thenReturn(List.of(
                looseGroup(9, 3, List.of("Asignación", "Ocurrencia"), RevisionKind.MODIFIED, null)));
        when(repository.countGroups(any())).thenReturn(1L);

        Page<AuditLogEntryDto> page = service.findAll(filter(null), PageRequest.of(0, 10));

        AuditLogEntryDto transaction = page.getContent().getFirst();
        assertThat(transaction.type()).isEqualTo(AuditLogEntryType.TRANSACTION);
        assertThat(transaction.operationId()).isNull();
        assertThat(transaction.entityType()).isNull();
        assertThat(transaction.recordId()).isNull();
        assertThat(transaction.revision()).isEqualTo(9);
        assertThat(transaction.recordCount()).isEqualTo(3);
        assertThat(transaction.entityTypes()).containsExactly("Asignación", "Ocurrencia");
        assertThat(transaction.kind()).isEqualTo(RevisionKind.MODIFIED);
        assertThat(transaction.description()).isEqualTo("3 cambios en Asignación, Ocurrencia");
    }

    @Test
    @DisplayName("TRANSACTION con kinds mezclados trae kind null")
    void findAll_transactionWithMixedKinds_hasNullKind() {
        when(repository.findGroups(any(), any())).thenReturn(List.of(
                looseGroup(9, 2, List.of("Ocurrencia"), null, null)));
        when(repository.countGroups(any())).thenReturn(1L);

        Page<AuditLogEntryDto> page = service.findAll(filter(null), PageRequest.of(0, 10));

        assertThat(page.getContent().getFirst().kind()).isNull();
    }

    @Test
    @DisplayName("conserva el orden que devuelve el repositorio y mezcla los tres tipos")
    void findAll_keepsRepositoryOrder() {
        when(repository.findGroups(any(), any())).thenReturn(List.of(
                operationGroup(30, "op-1", "Algo", 2),
                looseGroup(20, 2, List.of("Configuración"), RevisionKind.MODIFIED, null),
                looseGroup(10, 1, List.of("Configuración"), RevisionKind.MODIFIED, null)));
        when(repository.countGroups(any())).thenReturn(3L);

        Page<AuditLogEntryDto> page = service.findAll(filter(null), PageRequest.of(0, 10));

        assertThat(page.getContent()).extracting(AuditLogEntryDto::type).containsExactly(
                AuditLogEntryType.OPERATION, AuditLogEntryType.TRANSACTION, AuditLogEntryType.CHANGE);
        assertThat(page.getContent()).extracting(AuditLogEntryDto::revision).containsExactly(30, 20, 10);
    }

    @Test
    @DisplayName("totalElements es el de countGroups, no el tamaño de la lista, y el pageable llega al repositorio")
    void findAll_totalComesFromCount() {
        PageRequest pageable = PageRequest.of(2, 2);
        when(repository.countGroups(any())).thenReturn(57L);
        when(repository.findGroups(any(), any())).thenReturn(List.of(
                looseGroup(3, 1, List.of("Configuración"), RevisionKind.CREATED, null),
                looseGroup(2, 1, List.of("Configuración"), RevisionKind.CREATED, null)));

        Page<AuditLogEntryDto> page = service.findAll(filter(null), pageable);

        assertThat(page.getContent()).hasSize(2);
        assertThat(page.getTotalElements()).isEqualTo(57);
        assertThat(page.getTotalPages()).isEqualTo(29);
        assertThat(page.getNumber()).isEqualTo(2);
        verify(repository).findGroups(any(), org.mockito.ArgumentMatchers.eq(pageable));
    }

    @Test
    @DisplayName("sin entityType los targets son todas las entidades del registry")
    void findAll_withoutEntityType_targetsAllEntities() {
        service.findAll(filter(null), PageRequest.of(0, 10));

        assertThat(capturedGroupCriteria().targets()).containsExactly(ALLOCATION, SETTING);
    }

    @Test
    @DisplayName("con entityType los targets son sólo esa entidad")
    void findAll_withEntityType_targetsOnlyThatEntity() {
        service.findAll(filter("Configuración"), PageRequest.of(0, 10));

        assertThat(capturedGroupCriteria().targets()).containsExactly(SETTING);
    }

    @Test
    @DisplayName("entityType desconocido -> InvalidSelectionException sin tocar el repositorio")
    void findAll_unknownEntityType_throwsWithoutQuerying() {
        assertThatThrownBy(() -> service.findAll(filter("NoExiste"), PageRequest.of(0, 10)))
                .isInstanceOf(InvalidSelectionException.class);

        verifyNoInteractions(repository);
    }

    @Test
    @DisplayName("'to' anterior a 'from' -> InvalidDateRangeException sin tocar el repositorio")
    void findAll_toBeforeFrom_throws() {
        AuditLogFilter bad = new AuditLogFilter(
                LocalDate.of(2026, 1, 10), LocalDate.of(2026, 1, 1), null, null, null, null, null);

        assertThatThrownBy(() -> service.findAll(bad, PageRequest.of(0, 10)))
                .isInstanceOf(InvalidDateRangeException.class);

        verifyNoInteractions(repository);
    }

    @Test
    @DisplayName("'to' sin 'from' no lanza y deja 'from' nulo")
    void findAll_toWithoutFrom_doesNotThrow() {
        LocalDate to = LocalDate.of(2026, 3, 1);

        Page<AuditLogEntryDto> result = service.findAll(
                new AuditLogFilter(null, to, null, null, null, null, null), PageRequest.of(0, 10));

        assertThat(result.getContent()).isEmpty();
        AuditLogCriteria criteria = capturedGroupCriteria();
        assertThat(criteria.from()).isNull();
        assertThat(criteria.toExclusive()).isEqualTo(LocalDateTime.of(2026, 3, 2, 0, 0));
    }

    @Test
    @DisplayName("'from' y 'to' iguales: from a las 00:00 y toExclusive al día siguiente a las 00:00")
    void findAll_sameDayRange_coversWholeDay() {
        LocalDate day = LocalDate.of(2026, 1, 10);

        service.findAll(new AuditLogFilter(day, day, null, null, null, null, null), PageRequest.of(0, 10));

        AuditLogCriteria criteria = capturedGroupCriteria();
        assertThat(criteria.from()).isEqualTo(LocalDateTime.of(2026, 1, 10, 0, 0));
        assertThat(criteria.toExclusive()).isEqualTo(LocalDateTime.of(2026, 1, 11, 0, 0));
    }

    @Test
    @DisplayName("sin fechas, 'from' y 'toExclusive' quedan nulos")
    void findAll_withoutDates_leavesBoundsNull() {
        service.findAll(filter(null), PageRequest.of(0, 10));

        AuditLogCriteria criteria = capturedGroupCriteria();
        assertThat(criteria.from()).isNull();
        assertThat(criteria.toExclusive()).isNull();
    }

    @Test
    @DisplayName("propaga usuario y kind al criterio")
    void findAll_passesUserAndKind() {
        service.findAll(new AuditLogFilter(null, null, "someone@frc", null, RevisionKind.DELETED, null, null),
                PageRequest.of(0, 10));

        AuditLogCriteria criteria = capturedGroupCriteria();
        assertThat(criteria.user()).isEqualTo("someone@frc");
        assertThat(criteria.kind()).isEqualTo(RevisionKind.DELETED);
    }

    @Test
    @DisplayName("el criterio de countGroups es el mismo que el de findGroups")
    void findAll_countAndFindShareCriteria() {
        AuditLogFilter f = new AuditLogFilter(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 2), "u",
                "Configuración", RevisionKind.MODIFIED, null, null);

        service.findAll(f, PageRequest.of(0, 10));

        ArgumentCaptor<AuditLogCriteria> count = ArgumentCaptor.forClass(AuditLogCriteria.class);
        verify(repository).countGroups(count.capture());
        assertThat(count.getValue()).isEqualTo(capturedGroupCriteria());
    }

    @Test
    @DisplayName("findOperationItems pasa ChangeScope.ofOperation y mapea las filas a CHANGE con el total del count")
    void findOperationItems_usesOperationScope() {
        RevisionMetadata first = new RevisionMetadata("1", 21, DATE, "user@frc", ActorType.HUMAN, RevisionKind.CREATED,
                "Reasignación en lote", "op-9");
        RevisionMetadata second = new RevisionMetadata("2", 20, DATE, "user@frc", ActorType.HUMAN, RevisionKind.MODIFIED,
                "Reasignación en lote", "op-9");
        when(repository.countChanges(any(), any())).thenReturn(40L);
        when(repository.findChanges(any(), any(), any())).thenReturn(List.of(
                new AuditChangeRow(first, ALLOCATION), new AuditChangeRow(second, SETTING)));

        Page<AuditLogEntryDto> page = service.findOperationItems("op-9", filter(null), PageRequest.of(0, 2));

        assertThat(page.getTotalElements()).isEqualTo(40);
        assertThat(page.getContent()).extracting(AuditLogEntryDto::type).containsOnly(AuditLogEntryType.CHANGE);
        assertThat(page.getContent()).extracting(AuditLogEntryDto::revision).containsExactly(21, 20);
        assertThat(page.getContent()).extracting(AuditLogEntryDto::entityType)
                .containsExactly("Asignación", "Configuración");
        assertThat(page.getContent()).extracting(AuditLogEntryDto::operationId).containsOnly("op-9");
        verify(repository).countChanges(any(), org.mockito.ArgumentMatchers.eq(ChangeScope.ofOperation("op-9")));
        verify(repository).findChanges(any(), org.mockito.ArgumentMatchers.eq(ChangeScope.ofOperation("op-9")), any());
    }

    @Test
    @DisplayName("findOperationItems carga el estado con una sola llamada a stateRepository.load por página, con la clave de cada fila")
    @SuppressWarnings("unchecked")
    void findOperationItems_loadsStatesOncePerPage() {
        RevisionMetadata first = new RevisionMetadata("1", 21, DATE, "user@frc", ActorType.HUMAN, RevisionKind.CREATED,
                "Reasignación en lote", "op-9");
        RevisionMetadata second = new RevisionMetadata("2", 20, DATE, "user@frc", ActorType.HUMAN, RevisionKind.MODIFIED,
                "Reasignación en lote", "op-9");
        when(repository.countChanges(any(), any())).thenReturn(2L);
        when(repository.findChanges(any(), any(), any())).thenReturn(List.of(
                new AuditChangeRow(first, ALLOCATION), new AuditChangeRow(second, SETTING)));

        service.findOperationItems("op-9", filter(null), PageRequest.of(0, 2));

        ArgumentCaptor<Collection<RecordRevision>> keys = ArgumentCaptor.forClass(Collection.class);
        verify(stateRepository, times(1)).load(keys.capture());
        assertThat(keys.getValue()).containsExactlyInAnyOrder(
                new RecordRevision(ALLOCATION, "1", 21), new RecordRevision(SETTING, "2", 20));
    }

    @Test
    @DisplayName("findRevisionItems también carga el estado una vez por página")
    void findRevisionItems_loadsStatesOncePerPage() {
        when(repository.countChanges(any(), any())).thenReturn(0L);

        service.findRevisionItems(77, filter(null), PageRequest.of(0, 10));

        verify(stateRepository, times(1)).load(any());
    }

    @Test
    @DisplayName("findAll no consulta stateRepository y sus entradas traen changes null")
    void findAll_doesNotLoadStates() {
        when(repository.countGroups(any())).thenReturn(3L);
        when(repository.findGroups(any(), any())).thenReturn(List.of(
                operationGroup(11, "op-1", "Asignación de aulas en lote", 3),
                looseGroup(5, 1, List.of("Asignación"), RevisionKind.CREATED, null),
                looseGroup(4, 2, List.of("Asignación", "Configuración"), RevisionKind.MODIFIED, null)));

        Page<AuditLogEntryDto> page = service.findAll(filter(null), PageRequest.of(0, 10));

        assertThat(page.getContent()).extracting(AuditLogEntryDto::type).containsExactly(
                AuditLogEntryType.OPERATION, AuditLogEntryType.CHANGE, AuditLogEntryType.TRANSACTION);
        assertThat(page.getContent()).allSatisfy(entry -> assertThat(entry.changes()).isNull());
        verifyNoInteractions(stateRepository);
    }

    @Test
    @DisplayName("el diff de cada fila usa su estado cargado y una fila sin estado cargado queda con changes null")
    void findOperationItems_buildsChangesFromLoadedStates() {
        AuditedEntity withColumns = new AuditedEntity(Integer.class, "Setting", "Configuración", "configuracion_aud",
                "clave", String.class, List.of(new AuditedEntity.AuditedColumn("configuracion_aud", "valor", "value")));
        RevisionMetadata loaded = new RevisionMetadata("k1", 31, DATE, "user@frc", ActorType.HUMAN,
                RevisionKind.MODIFIED, "Modificación de configuración", "op-1");
        RevisionMetadata missing = new RevisionMetadata("k2", 30, DATE, "user@frc", ActorType.HUMAN,
                RevisionKind.MODIFIED, "Modificación de configuración", "op-1");
        when(repository.countChanges(any(), any())).thenReturn(2L);
        when(repository.findChanges(any(), any(), any())).thenReturn(List.of(
                new AuditChangeRow(loaded, withColumns), new AuditChangeRow(missing, withColumns)));
        when(stateRepository.load(any())).thenReturn(Map.of(
                new RecordRevision(withColumns, "k1", 31),
                new RecordStates(Map.of("value", "B"), Map.of("value", "A"))));

        Page<AuditLogEntryDto> page = service.findOperationItems("op-1", filter(null), PageRequest.of(0, 2));

        assertThat(page.getContent().get(0).changes()).containsExactly(new FieldChangeDto("value", "A", "B"));
        assertThat(page.getContent().get(1).changes()).isNull();
    }

    @Test
    @DisplayName("un MODIFIED con estado cargado y sin campos auditados distintos queda con changes de lista vacía, no null")
    void findOperationItems_modifiedWithoutAuditedChangesHasEmptyList() {
        AuditedEntity withColumns = new AuditedEntity(Integer.class, "Setting", "Configuración", "configuracion_aud",
                "clave", String.class, List.of(new AuditedEntity.AuditedColumn("configuracion_aud", "valor", "value")));
        RevisionMetadata metadata = new RevisionMetadata("k1", 31, DATE, "user@frc", ActorType.HUMAN,
                RevisionKind.MODIFIED, "Modificación de configuración", "op-1");
        when(repository.countChanges(any(), any())).thenReturn(1L);
        when(repository.findChanges(any(), any(), any())).thenReturn(List.of(new AuditChangeRow(metadata, withColumns)));
        when(stateRepository.load(any())).thenReturn(Map.of(
                new RecordRevision(withColumns, "k1", 31),
                new RecordStates(Map.of("value", "A"), Map.of("value", "A"))));

        Page<AuditLogEntryDto> page = service.findOperationItems("op-1", filter(null), PageRequest.of(0, 1));

        assertThat(page.getContent().getFirst().changes()).isNotNull().isEmpty();
    }

    @Test
    @DisplayName("findRevisionItems pasa ChangeScope.ofRevision")
    void findRevisionItems_usesRevisionScope() {
        when(repository.countChanges(any(), any())).thenReturn(0L);

        service.findRevisionItems(77, filter(null), PageRequest.of(0, 10));

        verify(repository).countChanges(any(), org.mockito.ArgumentMatchers.eq(ChangeScope.ofRevision(77)));
        verify(repository).findChanges(any(), org.mockito.ArgumentMatchers.eq(ChangeScope.ofRevision(77)), any());
    }

    @Test
    @DisplayName("los drill-downs aplican los filtros: entityType acota targets y las fechas llegan al criterio")
    void drillDowns_applyFilters() {
        AuditLogFilter f = new AuditLogFilter(LocalDate.of(2026, 2, 1), LocalDate.of(2026, 2, 1), null,
                "Asignación", RevisionKind.CREATED, null, null);

        service.findRevisionItems(5, f, PageRequest.of(0, 10));
        service.findOperationItems("op", f, PageRequest.of(0, 10));

        ArgumentCaptor<AuditLogCriteria> captor = ArgumentCaptor.forClass(AuditLogCriteria.class);
        verify(repository, org.mockito.Mockito.times(2)).findChanges(captor.capture(), any(), any());
        assertThat(captor.getAllValues()).allSatisfy(criteria -> {
            assertThat(criteria.targets()).containsExactly(ALLOCATION);
            assertThat(criteria.kind()).isEqualTo(RevisionKind.CREATED);
            assertThat(criteria.from()).isEqualTo(LocalDateTime.of(2026, 2, 1, 0, 0));
            assertThat(criteria.toExclusive()).isEqualTo(LocalDateTime.of(2026, 2, 2, 0, 0));
        });
    }

    @Test
    @DisplayName("los drill-downs validan entityType desconocido y rango de fechas")
    void drillDowns_validateFilters() {
        assertThatThrownBy(() -> service.findRevisionItems(1, filter("NoExiste"), PageRequest.of(0, 10)))
                .isInstanceOf(InvalidSelectionException.class);
        AuditLogFilter bad = new AuditLogFilter(LocalDate.of(2026, 1, 10), LocalDate.of(2026, 1, 1), null, null, null, null, null);
        assertThatThrownBy(() -> service.findOperationItems("op", bad, PageRequest.of(0, 10)))
                .isInstanceOf(InvalidDateRangeException.class);

        verifyNoInteractions(repository);
    }

    @Test
    @DisplayName("findEntityTypes devuelve las etiquetas del registry en su orden")
    void findEntityTypes_returnsRegistryLabelsInOrder() {
        assertThat(service.findEntityTypes()).containsExactly("Asignación", "Configuración");
    }
}
