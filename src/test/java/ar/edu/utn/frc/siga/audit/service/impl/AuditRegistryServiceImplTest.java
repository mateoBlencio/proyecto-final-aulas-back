package ar.edu.utn.frc.siga.audit.service.impl;

import ar.edu.utn.frc.siga.audit.RevisionKind;
import ar.edu.utn.frc.siga.audit.RevisionMetadata;
import ar.edu.utn.frc.siga.audit.RevisionReader;
import ar.edu.utn.frc.siga.audit.dto.AuditLogFilter;
import ar.edu.utn.frc.siga.audit.dto.response.AuditLogEntryDto;
import ar.edu.utn.frc.siga.audit.dto.response.AuditLogEntryType;
import ar.edu.utn.frc.siga.audit.mapper.AuditLogEntryMapperImpl;
import ar.edu.utn.frc.siga.audit.registry.AuditedEntity;
import ar.edu.utn.frc.siga.audit.registry.AuditedEntityRegistry;
import ar.edu.utn.frc.siga.common.exception.InvalidDateRangeException;
import ar.edu.utn.frc.siga.common.exception.InvalidSelectionException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("AuditRegistryServiceImpl")
class AuditRegistryServiceImplTest {

    private static final AuditedEntity ALLOCATION = new AuditedEntity(String.class, "Allocation", "Asignación");
    private static final AuditedEntity SETTING = new AuditedEntity(Integer.class, "Setting", "Configuración");

    @Mock
    private RevisionReader revisionReader;
    @Mock
    private AuditedEntityRegistry registry;

    private AuditRegistryServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new AuditRegistryServiceImpl(revisionReader, registry, new AuditLogEntryMapperImpl());
        lenient().when(registry.all()).thenReturn(List.of(ALLOCATION, SETTING));
        lenient().when(registry.byLabel("Asignación")).thenReturn(Optional.of(ALLOCATION));
        lenient().when(registry.byLabel("Configuración")).thenReturn(Optional.of(SETTING));
        lenient().when(registry.byLabel("NoExiste")).thenReturn(Optional.empty());
    }

    private static RevisionMetadata change(int revision, RevisionKind kind) {
        return new RevisionMetadata(String.valueOf(revision), revision, LocalDateTime.now(),
                "user@frc", kind, "Se modificó algo", null);
    }

    private static RevisionMetadata opRow(int revision, String operationId, String description) {
        return new RevisionMetadata(String.valueOf(revision), revision, LocalDateTime.now(),
                "user@frc", RevisionKind.CREATED, description, operationId);
    }

    private AuditLogFilter filter(String entityType) {
        return new AuditLogFilter(null, null, null, entityType, null);
    }

    @Test
    @DisplayName("une cambios sueltos de varios tipos y ordena por revisión descendente")
    void findAll_ordersLooseChangesByRevisionDescending() {
        when(revisionReader.readMetadata(eq(String.class), any(), any(), any(), any(), isNull()))
                .thenReturn(List.of(change(1, RevisionKind.CREATED), change(4, RevisionKind.MODIFIED)));
        when(revisionReader.readMetadata(eq(Integer.class), any(), any(), any(), any(), isNull()))
                .thenReturn(List.of(change(2, RevisionKind.CREATED), change(3, RevisionKind.MODIFIED)));

        Page<AuditLogEntryDto> page = service.findAll(filter(null), PageRequest.of(0, 10));

        assertThat(page.getContent()).extracting(AuditLogEntryDto::revision).containsExactly(4, 3, 2, 1);
        assertThat(page.getContent()).extracting(AuditLogEntryDto::type)
                .containsOnly(AuditLogEntryType.CHANGE);
    }

    @Test
    @DisplayName("agrupa las revisiones de una misma operación en una entrada OPERATION")
    void findAll_groupsOperationRevisionsIntoOneEntry() {
        when(revisionReader.readMetadata(eq(String.class), any(), any(), any(), any(), isNull()))
                .thenReturn(List.of(
                        opRow(10, "op-1", "Asignación de aulas en lote"),
                        opRow(10, "op-1", "Asignación de aulas en lote"),
                        opRow(11, "op-1", "Asignación de aulas en lote")));
        when(revisionReader.readMetadata(eq(Integer.class), any(), any(), any(), any(), isNull()))
                .thenReturn(List.of(change(5, RevisionKind.MODIFIED)));

        Page<AuditLogEntryDto> page = service.findAll(filter(null), PageRequest.of(0, 10));

        assertThat(page.getContent()).hasSize(2);
        AuditLogEntryDto operation = page.getContent().getFirst();
        assertThat(operation.type()).isEqualTo(AuditLogEntryType.OPERATION);
        assertThat(operation.operationId()).isEqualTo("op-1");
        assertThat(operation.description()).isEqualTo("Asignación de aulas en lote");
        assertThat(operation.recordCount()).isEqualTo(3);
        assertThat(operation.revision()).isEqualTo(11);
        assertThat(operation.entityTypes()).containsExactly("Asignación");
        assertThat(operation.kind()).isNull();
        assertThat(operation.recordId()).isNull();
        assertThat(page.getContent().get(1).type()).isEqualTo(AuditLogEntryType.CHANGE);
    }

    @Test
    @DisplayName("pagina en memoria")
    void findAll_paginatesInMemory() {
        when(revisionReader.readMetadata(eq(String.class), any(), any(), any(), any(), isNull()))
                .thenReturn(List.of(change(1, RevisionKind.CREATED), change(2, RevisionKind.CREATED),
                        change(3, RevisionKind.CREATED), change(4, RevisionKind.CREATED), change(5, RevisionKind.CREATED)));
        when(revisionReader.readMetadata(eq(Integer.class), any(), any(), any(), any(), isNull())).thenReturn(List.of());

        Page<AuditLogEntryDto> page = service.findAll(filter(null), PageRequest.of(1, 2));

        assertThat(page.getTotalElements()).isEqualTo(5);
        assertThat(page.getTotalPages()).isEqualTo(3);
        assertThat(page.getContent()).extracting(AuditLogEntryDto::revision).containsExactly(3, 2);
    }

    @Test
    @DisplayName("con entityType consulta sólo ese tipo")
    void findAll_withEntityType_queriesOnlyThatType() {
        when(revisionReader.readMetadata(eq(Integer.class), any(), any(), any(), any(), isNull()))
                .thenReturn(List.of(change(1, RevisionKind.CREATED)));

        Page<AuditLogEntryDto> page = service.findAll(filter("Configuración"), PageRequest.of(0, 10));

        assertThat(page.getContent()).singleElement()
                .extracting(AuditLogEntryDto::entityType).isEqualTo("Configuración");
        verify(revisionReader).readMetadata(eq(Integer.class), any(), any(), any(), any(), isNull());
        verify(revisionReader, never()).readMetadata(eq(String.class), any(), any(), any(), any(), isNull());
    }

    @Test
    @DisplayName("entityType desconocido -> InvalidSelectionException")
    void findAll_unknownEntityType_throws() {
        assertThatThrownBy(() -> service.findAll(filter("NoExiste"), PageRequest.of(0, 10)))
                .isInstanceOf(InvalidSelectionException.class);
    }

    @Test
    @DisplayName("'to' anterior a 'from' -> InvalidDateRangeException")
    void findAll_toBeforeFrom_throws() {
        AuditLogFilter bad = new AuditLogFilter(
                LocalDate.of(2026, 1, 10), LocalDate.of(2026, 1, 1), null, null, null);
        assertThatThrownBy(() -> service.findAll(bad, PageRequest.of(0, 10)))
                .isInstanceOf(InvalidDateRangeException.class);
    }

    @Test
    @DisplayName("traduce el rango de fechas a límites de día y propaga usuario y kind")
    void findAll_passesDayBoundsUserAndKind() {
        when(revisionReader.readMetadata(any(), any(), any(), any(), any(), isNull())).thenReturn(List.of());
        AuditLogFilter f = new AuditLogFilter(
                LocalDate.of(2026, 1, 10), LocalDate.of(2026, 1, 10), "someone@frc", null, RevisionKind.MODIFIED);

        service.findAll(f, PageRequest.of(0, 10));

        verify(revisionReader).readMetadata(String.class,
                LocalDate.of(2026, 1, 10).atStartOfDay(),
                LocalDate.of(2026, 1, 11).atStartOfDay(),
                "someone@frc", RevisionKind.MODIFIED, null);
        verify(revisionReader).readMetadata(Integer.class,
                LocalDate.of(2026, 1, 10).atStartOfDay(),
                LocalDate.of(2026, 1, 11).atStartOfDay(),
                "someone@frc", RevisionKind.MODIFIED, null);
    }

    @Test
    @DisplayName("findOperationItems devuelve los cambios de la operación como entradas CHANGE, paginados")
    void findOperationItems_returnsChangesOfOperation() {
        when(revisionReader.readMetadata(eq(String.class), isNull(), isNull(), isNull(), isNull(), eq("op-9")))
                .thenReturn(List.of(opRow(20, "op-9", "Reasignación en lote"), opRow(21, "op-9", "Reasignación en lote")));
        when(revisionReader.readMetadata(eq(Integer.class), isNull(), isNull(), isNull(), isNull(), eq("op-9")))
                .thenReturn(List.of());

        Page<AuditLogEntryDto> page = service.findOperationItems("op-9", PageRequest.of(0, 10));

        assertThat(page.getTotalElements()).isEqualTo(2);
        assertThat(page.getContent()).extracting(AuditLogEntryDto::type).containsOnly(AuditLogEntryType.CHANGE);
        assertThat(page.getContent()).extracting(AuditLogEntryDto::revision).containsExactly(21, 20);
        assertThat(page.getContent()).extracting(AuditLogEntryDto::operationId).containsOnly("op-9");
    }
}
