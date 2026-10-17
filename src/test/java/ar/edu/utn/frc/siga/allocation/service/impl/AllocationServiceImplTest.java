package ar.edu.utn.frc.siga.allocation.service.impl;

import ar.edu.utn.frc.siga.allocation.dto.response.AllocationResponseDto;
import ar.edu.utn.frc.siga.allocation.dto.response.DeallocatedOccurrenceDto;
import ar.edu.utn.frc.siga.allocation.dto.response.ReleasedOccurrenceDto;
import ar.edu.utn.frc.siga.allocation.service.command.ReleaseCommand;
import ar.edu.utn.frc.siga.events.model.ReleaseReason;
import ar.edu.utn.frc.siga.events.service.command.OccurrenceRelease;
import ar.edu.utn.frc.siga.allocation.exception.AllocationConflictException;
import ar.edu.utn.frc.siga.allocation.exception.ReallocationConflictException;
import ar.edu.utn.frc.siga.allocation.mapper.AllocationComposer;
import ar.edu.utn.frc.siga.allocation.model.Allocation;
import ar.edu.utn.frc.siga.allocation.model.AllocationSource;
import ar.edu.utn.frc.siga.allocation.repository.AllocationRepository;
import ar.edu.utn.frc.siga.allocation.service.command.AllocationCommand;
import ar.edu.utn.frc.siga.allocation.service.command.AllocationItem;
import ar.edu.utn.frc.siga.allocation.service.command.AllocationTarget;
import ar.edu.utn.frc.siga.allocation.service.command.DeallocationCommand;
import ar.edu.utn.frc.siga.allocation.validator.AllocationCandidate;
import ar.edu.utn.frc.siga.allocation.validator.AllocationValidator;
import ar.edu.utn.frc.siga.audit.internal.AuditDescriptionProbe;
import ar.edu.utn.frc.siga.common.exception.ResourceNotFoundException;
import ar.edu.utn.frc.siga.common.security.BuildingScope;
import ar.edu.utn.frc.siga.common.security.BuildingScopeResolver;
import ar.edu.utn.frc.siga.common.security.Permission;
import ar.edu.utn.frc.siga.events.dto.response.OccurrenceSlotDto;
import ar.edu.utn.frc.siga.events.model.OccurrenceStatus;
import ar.edu.utn.frc.siga.events.service.OccurrenceService;
import ar.edu.utn.frc.siga.space.dto.response.ClassroomResponseDto;
import ar.edu.utn.frc.siga.space.service.ClassroomService;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.time.LocalDate;
import java.time.Instant;
import java.time.LocalTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Unit tests de {@link AllocationServiceImpl}: exercita la orquestación (no la lógica de
 * {@link AllocationTargetResolver}/{@link AllocationWriter}/{@link AllocationValidator}, que
 * están mockeados acá) para los tres verbos ({@code allocate}/{@code reallocate}/{@code deallocate})
 * cruzados con las dos formas de {@link AllocationTarget} y batch-de-1/batch-de-N.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("AllocationServiceImpl")
class AllocationServiceImplTest {

    @Mock
    private AllocationRepository allocationRepository;
    @Mock
    private OccurrenceService occurrenceService;
    @Mock
    private AllocationComposer composer;
    @Mock
    private AllocationValidator validator;
    @Mock
    private AllocationTargetResolver targetResolver;
    @Mock
    private AllocationWriter writer;
    @Mock
    private ClassroomService classroomService;
    @Mock
    private BuildingScopeResolver buildingScopeResolver;
    @Mock
    private ClassroomAllocationLock classroomLock;

    private AllocationServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new AllocationServiceImpl(allocationRepository, occurrenceService, composer, validator, targetResolver,
                writer, classroomService, buildingScopeResolver, classroomLock);
        lenient().when(classroomService.findByIdsIncludingDeactivated(any())).thenReturn(List.of());
        lenient().when(buildingScopeResolver.scopeFor(any())).thenReturn(BuildingScope.unrestricted());
    }

    // ---------- allocate ----------

    @Test
    @DisplayName("allocate: target Occurrences, batch de 1, aula libre → crea y valida solapamiento (MANUAL)")
    void allocateOccurrencesTargetBatchDeUnoCrea() {
        AllocationItem item = new AllocationItem(new AllocationTarget.Occurrences(List.of(10L)), 5L);
        AllocationCommand command = AllocationCommand.manual(List.of(item), "obs");
        OccurrenceSlotDto occ = occurrenceSlot(10L, 1L, futureDate(1));
        Map<OccurrenceSlotDto, Long> resolved = mapOf(occ, 5);
        when(targetResolver.resolveClassroomByOccurrence(eq(command.items()), eq(LocalDate.now()))).thenReturn(assignments(resolved));
        Allocation saved = allocation(100L, 10L, 5, AllocationSource.MANUAL);
        when(writer.create(resolved, "obs", AllocationSource.MANUAL)).thenReturn(List.of(saved));
        when(composer.composeAll(List.of(saved))).thenReturn(List.of(dummyResponseDto()));

        List<AllocationResponseDto> result = service.allocate(command);

        assertThat(result).hasSize(1);
        verify(validator).validateClassroomsAvailable(Set.of(5L));
        ArgumentCaptor<List<AllocationCandidate>> captor = ArgumentCaptor.forClass(List.class);
        verify(validator).validateManualOverlap(captor.capture(), any());
        assertThat(captor.getValue()).containsExactly(new AllocationCandidate(occ, 5L));
        verify(writer).create(resolved, "obs", AllocationSource.MANUAL);
    }

    @Test
    @DisplayName("allocate: la observación del comando llega intacta al validador (la exige validateManualOverlap si hay solape tolerado)")
    void allocatePasaLaObservacionAlValidator() {
        AllocationItem item = new AllocationItem(new AllocationTarget.Occurrences(List.of(10L)), 5L);
        AllocationCommand command = AllocationCommand.manual(List.of(item), "mudanza de aula por obra");
        OccurrenceSlotDto occ = occurrenceSlot(10L, 1L, futureDate(1));
        Map<OccurrenceSlotDto, Long> resolved = mapOf(occ, 5);
        when(targetResolver.resolveClassroomByOccurrence(eq(command.items()), eq(LocalDate.now()))).thenReturn(assignments(resolved));
        when(writer.create(resolved, "mudanza de aula por obra", AllocationSource.MANUAL))
                .thenReturn(List.of(allocation(100L, 10L, 5, AllocationSource.MANUAL)));

        service.allocate(command);

        verify(validator).validateManualOverlap(anyList(), eq("mudanza de aula por obra"));
    }

    @Test
    @DisplayName("allocate: target Event, batch de N (una sola occurrence del evento se resuelve en varias) → crea todas")
    void allocateEventTargetBatchDeNCrea() {
        AllocationItem item = new AllocationItem(new AllocationTarget.Event(1L), 5L);
        AllocationCommand command = AllocationCommand.manual(List.of(item), "obs");
        OccurrenceSlotDto occ1 = occurrenceSlot(10L, 1L, futureDate(1));
        OccurrenceSlotDto occ2 = occurrenceSlot(11L, 1L, futureDate(8));
        OccurrenceSlotDto occ3 = occurrenceSlot(12L, 1L, futureDate(15));
        Map<OccurrenceSlotDto, Long> resolved = mapOf(occ1, 5, occ2, 5, occ3, 5);
        when(targetResolver.resolveClassroomByOccurrence(eq(command.items()), eq(LocalDate.now()))).thenReturn(assignments(resolved));
        List<Allocation> saved = List.of(
                allocation(100L, 10L, 5, AllocationSource.MANUAL),
                allocation(101L, 11L, 5, AllocationSource.MANUAL),
                allocation(102L, 12L, 5, AllocationSource.MANUAL));
        when(writer.create(resolved, "obs", AllocationSource.MANUAL)).thenReturn(saved);
        when(composer.composeAll(saved)).thenReturn(List.of(dummyResponseDto(), dummyResponseDto(), dummyResponseDto()));

        List<AllocationResponseDto> result = service.allocate(command);

        assertThat(result).hasSize(3);
        verify(writer).create(resolved, "obs", AllocationSource.MANUAL);
    }

    @Test
    @DisplayName("allocate: múltiples items en el lote (batch de N a nivel comando) → resuelve juntos y crea juntos")
    void allocateMultiplesItemsBatchDeN() {
        AllocationItem item1 = new AllocationItem(new AllocationTarget.Occurrences(List.of(10L)), 5L);
        AllocationItem item2 = new AllocationItem(new AllocationTarget.Occurrences(List.of(11L)), 6L);
        AllocationCommand command = AllocationCommand.manual(List.of(item1, item2), "obs");
        OccurrenceSlotDto occ1 = occurrenceSlot(10L, 1L, futureDate(1));
        OccurrenceSlotDto occ2 = occurrenceSlot(11L, 2L, futureDate(2));
        Map<OccurrenceSlotDto, Long> resolved = mapOf(occ1, 5, occ2, 6);
        when(targetResolver.resolveClassroomByOccurrence(eq(command.items()), eq(LocalDate.now()))).thenReturn(assignments(resolved));
        List<Allocation> saved = List.of(allocation(100L, 10L, 5, AllocationSource.MANUAL), allocation(101L, 11L, 6, AllocationSource.MANUAL));
        when(writer.create(resolved, "obs", AllocationSource.MANUAL)).thenReturn(saved);
        when(composer.composeAll(saved)).thenReturn(List.of(dummyResponseDto(), dummyResponseDto()));

        List<AllocationResponseDto> result = service.allocate(command);

        assertThat(result).hasSize(2);
        verify(validator).validateClassroomsAvailable(Set.of(5L, 6L));
    }

    @Test
    @DisplayName("allocate: lote que no resuelve a ninguna occurrence es un no-op (sin validar aulas ni solapamiento)")
    void allocateTargetVacioEsNoOp() {
        AllocationItem item = new AllocationItem(new AllocationTarget.Event(1L), 5L);
        AllocationCommand command = AllocationCommand.manual(List.of(item), "obs");
        when(targetResolver.resolveClassroomByOccurrence(eq(command.items()), eq(LocalDate.now()))).thenReturn(assignments(Map.of()));
        when(writer.create(Map.of(), "obs", AllocationSource.MANUAL)).thenReturn(List.of());

        List<AllocationResponseDto> result = service.allocate(command);

        assertThat(result).isEmpty();
        verify(validator, never()).validateClassroomsAvailable(any());
        verify(validator, never()).validateManualOverlap(anyList(), any());
        verify(writer).create(Map.of(), "obs", AllocationSource.MANUAL);
    }

    @Test
    @DisplayName("allocate: aula no disponible → el validator corta antes de escribir")
    void allocateAulaNoDisponibleNoEscribe() {
        AllocationItem item = new AllocationItem(new AllocationTarget.Occurrences(List.of(10L)), 999L);
        AllocationCommand command = AllocationCommand.manual(List.of(item), "obs");
        OccurrenceSlotDto occ = occurrenceSlot(10L, 1L, futureDate(1));
        Map<OccurrenceSlotDto, Long> resolved = mapOf(occ, 999);
        when(targetResolver.resolveClassroomByOccurrence(eq(command.items()), eq(LocalDate.now()))).thenReturn(assignments(resolved));
        doThrow(new AllocationConflictException("aula 999 no existe")).when(validator).validateClassroomsAvailable(Set.of(999L));

        assertThatThrownBy(() -> service.allocate(command)).isInstanceOf(AllocationConflictException.class);

        verify(writer, never()).create(any(), any(), any());
        verify(validator, never()).validateManualOverlap(anyList(), any());
    }

    @Test
    @DisplayName("allocate: aula fuera del alcance del usuario → AccessDeniedException, no valida disponibilidad ni escribe")
    void allocateSinAlcanceSobreElEdificioNoEscribe() {
        AllocationItem item = new AllocationItem(new AllocationTarget.Occurrences(List.of(10L)), 5L);
        AllocationCommand command = AllocationCommand.manual(List.of(item), "obs");
        OccurrenceSlotDto occ = occurrenceSlot(10L, 1L, futureDate(1));
        Map<OccurrenceSlotDto, Long> resolved = mapOf(occ, 5);
        when(targetResolver.resolveClassroomByOccurrence(eq(command.items()), eq(LocalDate.now()))).thenReturn(assignments(resolved));
        ClassroomResponseDto classroom = new ClassroomResponseDto(5L, 101, 40, 9L, "Edificio Ajeno", 1L, "Normal", null, true);
        when(classroomService.findByIdsIncludingDeactivated(Set.of(5L))).thenReturn(List.of(classroom));
        doThrow(new AccessDeniedException("sin acceso"))
                .when(buildingScopeResolver).requireAccess(Permission.ALLOCATION_WRITE, Set.of(9L));

        assertThatThrownBy(() -> service.allocate(command)).isInstanceOf(AccessDeniedException.class);

        verify(validator, never()).validateClassroomsAvailable(any());
        verify(writer, never()).create(any(), any(), any());
    }

    @Test
    @DisplayName("allocate: source MANUAL con solapamiento → el validator corta, writer.create nunca se llama (409, nada se escribe)")
    void allocateManualConSolapeNoEscribe() {
        AllocationItem item = new AllocationItem(new AllocationTarget.Occurrences(List.of(10L)), 5L);
        AllocationCommand command = AllocationCommand.manual(List.of(item), "obs");
        OccurrenceSlotDto occ = occurrenceSlot(10L, 1L, futureDate(1));
        Map<OccurrenceSlotDto, Long> resolved = mapOf(occ, 5);
        when(targetResolver.resolveClassroomByOccurrence(eq(command.items()), eq(LocalDate.now()))).thenReturn(assignments(resolved));
        doThrow(new ReallocationConflictException(List.of())).when(validator).validateManualOverlap(anyList(), any());

        assertThatThrownBy(() -> service.allocate(command)).isInstanceOf(ReallocationConflictException.class);

        verify(writer, never()).create(any(), any(), any());
    }

    @Test
    @DisplayName("allocate: source AUTOMATIC no valida solapamiento (ya lo validó el preview contra su propio snapshot)")
    void allocateAutomaticNoValidaSolapamiento() {
        AllocationItem item = new AllocationItem(new AllocationTarget.Occurrences(List.of(10L)), 5L);
        AllocationCommand command = AllocationCommand.automatic(List.of(item));
        OccurrenceSlotDto occ = occurrenceSlot(10L, 1L, futureDate(1));
        Map<OccurrenceSlotDto, Long> resolved = mapOf(occ, 5);
        when(targetResolver.resolveClassroomByOccurrence(eq(command.items()), eq(LocalDate.now()))).thenReturn(assignments(resolved));
        when(writer.create(resolved, null, AllocationSource.AUTOMATIC)).thenReturn(List.of(allocation(100L, 10L, 5, AllocationSource.AUTOMATIC)));

        service.allocate(command);

        verify(validator).validateClassroomsAvailable(Set.of(5L));
        // Requisito A: AUTOMATIC nunca ve el margen tolerado, ni siquiera indirectamente.
        verify(validator, never()).validateManualOverlap(anyList(), any());
    }

    @Test
    @DisplayName("allocate: source IMPORTED clampea a null (incluye pasadas) y no valida solapamiento")
    void allocateImportedClampeaNullYNoValidaSolapamiento() {
        AllocationItem item = new AllocationItem(new AllocationTarget.Event(1L), 5L);
        AllocationCommand command = AllocationCommand.imported(List.of(item), "Importado de Excel");
        OccurrenceSlotDto occ = occurrenceSlot(10L, 1L, LocalDate.now().minusDays(3));
        Map<OccurrenceSlotDto, Long> resolved = mapOf(occ, 5);
        when(targetResolver.resolveClassroomByOccurrence(eq(command.items()), isNull())).thenReturn(assignments(resolved));
        when(writer.create(resolved, "Importado de Excel", AllocationSource.IMPORTED))
                .thenReturn(List.of(allocation(100L, 10L, 5, AllocationSource.IMPORTED)));

        service.allocate(command);

        verify(targetResolver).resolveClassroomByOccurrence(command.items(), null);
        // Requisito A: IMPORTED nunca ve el margen tolerado, ni siquiera indirectamente.
        verify(validator, never()).validateManualOverlap(anyList(), any());
    }

    @Test
    @DisplayName("allocate: source MANUAL clampea a hoy (no toca el pasado)")
    void allocateManualClampeaHoy() {
        AllocationItem item = new AllocationItem(new AllocationTarget.Event(1L), 5L);
        AllocationCommand command = AllocationCommand.manual(List.of(item), "obs");
        when(targetResolver.resolveClassroomByOccurrence(eq(command.items()), eq(LocalDate.now()))).thenReturn(assignments(Map.of()));

        service.allocate(command);

        verify(targetResolver).resolveClassroomByOccurrence(command.items(), LocalDate.now());
    }

    // ---------- reallocate ----------

    @Test
    @DisplayName("reallocate: target Occurrences, batch de 1 → upsert (no create)")
    void reallocateOccurrencesTargetBatchDeUnoHaceUpsert() {
        AllocationItem item = new AllocationItem(new AllocationTarget.Occurrences(List.of(10L)), 5L);
        AllocationCommand command = AllocationCommand.manual(List.of(item), "cambio de aula");
        OccurrenceSlotDto occ = occurrenceSlot(10L, 1L, futureDate(1));
        Map<OccurrenceSlotDto, Long> resolved = mapOf(occ, 5);
        when(targetResolver.resolveClassroomByOccurrence(eq(command.items()), eq(LocalDate.now()))).thenReturn(assignments(resolved));
        Allocation saved = allocation(900L, 10L, 5, AllocationSource.MANUAL);
        when(writer.upsert(resolved, "cambio de aula", AllocationSource.MANUAL)).thenReturn(List.of(saved));
        when(composer.composeAll(List.of(saved))).thenReturn(List.of(dummyResponseDto()));

        List<AllocationResponseDto> result = service.reallocate(command);

        assertThat(result).hasSize(1);
        verify(writer).upsert(resolved, "cambio de aula", AllocationSource.MANUAL);
        verify(writer, never()).create(any(), any(), any());
    }

    @Test
    @DisplayName("reallocate: target Event, batch de N → upsert de todas las occurrences resueltas")
    void reallocateEventTargetBatchDeN() {
        AllocationItem item = new AllocationItem(new AllocationTarget.Event(1L), 7L);
        AllocationCommand command = AllocationCommand.manual(List.of(item), "obs");
        OccurrenceSlotDto occ1 = occurrenceSlot(10L, 1L, futureDate(1));
        OccurrenceSlotDto occ2 = occurrenceSlot(11L, 1L, futureDate(8));
        Map<OccurrenceSlotDto, Long> resolved = mapOf(occ1, 7, occ2, 7);
        when(targetResolver.resolveClassroomByOccurrence(eq(command.items()), eq(LocalDate.now()))).thenReturn(assignments(resolved));
        List<Allocation> saved = List.of(allocation(900L, 10L, 7, AllocationSource.MANUAL), allocation(901L, 11L, 7, AllocationSource.MANUAL));
        when(writer.upsert(resolved, "obs", AllocationSource.MANUAL)).thenReturn(saved);
        when(composer.composeAll(saved)).thenReturn(List.of(dummyResponseDto(), dummyResponseDto()));

        List<AllocationResponseDto> result = service.reallocate(command);

        assertThat(result).hasSize(2);
        verify(writer).upsert(resolved, "obs", AllocationSource.MANUAL);
    }

    @Test
    @DisplayName("reallocate: lote vacío es un no-op")
    void reallocateTargetVacioEsNoOp() {
        AllocationItem item = new AllocationItem(new AllocationTarget.Occurrences(List.of(10L)), 5L);
        AllocationCommand command = AllocationCommand.manual(List.of(item), "obs");
        when(targetResolver.resolveClassroomByOccurrence(eq(command.items()), eq(LocalDate.now()))).thenReturn(assignments(Map.of()));
        when(writer.upsert(Map.of(), "obs", AllocationSource.MANUAL)).thenReturn(List.of());

        List<AllocationResponseDto> result = service.reallocate(command);

        assertThat(result).isEmpty();
        verify(validator, never()).validateClassroomsAvailable(any());
        verify(validator, never()).validateManualOverlap(anyList(), any());
    }

    @Test
    @DisplayName("reallocate: source MANUAL con solapamiento → 409, writer.upsert nunca se llama")
    void reallocateManualConSolapeNoEscribe() {
        AllocationItem item = new AllocationItem(new AllocationTarget.Occurrences(List.of(10L)), 5L);
        AllocationCommand command = AllocationCommand.manual(List.of(item), "obs");
        OccurrenceSlotDto occ = occurrenceSlot(10L, 1L, futureDate(1));
        Map<OccurrenceSlotDto, Long> resolved = mapOf(occ, 5);
        when(targetResolver.resolveClassroomByOccurrence(eq(command.items()), eq(LocalDate.now()))).thenReturn(assignments(resolved));
        doThrow(new ReallocationConflictException(List.of())).when(validator).validateManualOverlap(anyList(), any());

        assertThatThrownBy(() -> service.reallocate(command)).isInstanceOf(ReallocationConflictException.class);

        verify(writer, never()).upsert(any(), any(), any());
    }

    @Test
    @DisplayName("reallocate: source AUTOMATIC no valida solapamiento")
    void reallocateAutomaticNoValidaSolapamiento() {
        AllocationItem item = new AllocationItem(new AllocationTarget.Occurrences(List.of(10L)), 5L);
        AllocationCommand command = AllocationCommand.automatic(List.of(item));
        OccurrenceSlotDto occ = occurrenceSlot(10L, 1L, futureDate(1));
        Map<OccurrenceSlotDto, Long> resolved = mapOf(occ, 5);
        when(targetResolver.resolveClassroomByOccurrence(eq(command.items()), eq(LocalDate.now()))).thenReturn(assignments(resolved));
        when(writer.upsert(resolved, null, AllocationSource.AUTOMATIC)).thenReturn(List.of(allocation(900L, 10L, 5, AllocationSource.AUTOMATIC)));

        service.reallocate(command);

        // Requisito A: AUTOMATIC nunca ve el margen tolerado, ni siquiera indirectamente.
        verify(validator, never()).validateManualOverlap(anyList(), any());
    }

    @Test
    @DisplayName("reallocate: source AUTOMATIC llama validateStrictOverlap con los candidatos del lote")
    void reallocateAutomaticLlamaValidateStrictOverlapConLosCandidatos() {
        AllocationItem item = new AllocationItem(new AllocationTarget.Occurrences(List.of(10L)), 5L);
        AllocationCommand command = AllocationCommand.automatic(List.of(item));
        OccurrenceSlotDto occ = occurrenceSlot(10L, 1L, futureDate(1));
        Map<OccurrenceSlotDto, Long> resolved = mapOf(occ, 5);
        when(targetResolver.resolveClassroomByOccurrence(eq(command.items()), eq(LocalDate.now()))).thenReturn(assignments(resolved));
        when(writer.upsert(resolved, null, AllocationSource.AUTOMATIC))
                .thenReturn(List.of(allocation(900L, 10L, 5, AllocationSource.AUTOMATIC)));

        service.reallocate(command);

        ArgumentCaptor<List<AllocationCandidate>> captor = ArgumentCaptor.forClass(List.class);
        verify(validator).validateStrictOverlap(captor.capture());
        assertThat(captor.getValue()).containsExactly(new AllocationCandidate(occ, 5L));
    }

    @Test
    @DisplayName("reallocate: source MANUAL llama validateManualOverlap y nunca validateStrictOverlap")
    void reallocateManualNuncaLlamaValidateStrictOverlap() {
        AllocationItem item = new AllocationItem(new AllocationTarget.Occurrences(List.of(10L)), 5L);
        AllocationCommand command = AllocationCommand.manual(List.of(item), "obs");
        OccurrenceSlotDto occ = occurrenceSlot(10L, 1L, futureDate(1));
        Map<OccurrenceSlotDto, Long> resolved = mapOf(occ, 5);
        when(targetResolver.resolveClassroomByOccurrence(eq(command.items()), eq(LocalDate.now()))).thenReturn(assignments(resolved));
        when(writer.upsert(resolved, "obs", AllocationSource.MANUAL))
                .thenReturn(List.of(allocation(900L, 10L, 5, AllocationSource.MANUAL)));

        service.reallocate(command);

        verify(validator).validateManualOverlap(anyList(), eq("obs"));
        verify(validator, never()).validateStrictOverlap(anyList());
    }

    @Test
    @DisplayName("reallocate: el lock por aula se toma antes que cualquier validación de solapamiento, con el set de aulas del lote")
    void reallocateTomaElLockAntesDeValidarSolapamiento() {
        AllocationItem item1 = new AllocationItem(new AllocationTarget.Occurrences(List.of(10L)), 5L);
        AllocationItem item2 = new AllocationItem(new AllocationTarget.Occurrences(List.of(11L)), 6L);
        AllocationCommand command = AllocationCommand.manual(List.of(item1, item2), "obs");
        OccurrenceSlotDto occ1 = occurrenceSlot(10L, 1L, futureDate(1));
        OccurrenceSlotDto occ2 = occurrenceSlot(11L, 2L, futureDate(2));
        Map<OccurrenceSlotDto, Long> resolved = mapOf(occ1, 5, occ2, 6);
        when(targetResolver.resolveClassroomByOccurrence(eq(command.items()), eq(LocalDate.now()))).thenReturn(assignments(resolved));
        when(writer.upsert(resolved, "obs", AllocationSource.MANUAL)).thenReturn(List.of());

        service.reallocate(command);

        InOrder order = inOrder(classroomLock, validator);
        order.verify(classroomLock).lock(Set.of(5L, 6L));
        order.verify(validator).validateManualOverlap(anyList(), any());
    }

    // ---------- release ----------

    private OccurrenceSlotDto slotWithPreviousClassroom(long id, Long previousClassroomId) {
        return new OccurrenceSlotDto(id, 1L, futureDate(1), LocalTime.of(8, 0), LocalTime.of(9, 30),
                OccurrenceStatus.ROOM_RELEASED, 30, ReleaseReason.VIRTUAL_CLASS, null, previousClassroomId, null);
    }

    private ReleaseCommand releaseCommand(Long... occurrenceIds) {
        return new ReleaseCommand(List.of(new AllocationTarget.Occurrences(List.of(occurrenceIds))),
                ReleaseReason.VIRTUAL_CLASS, "cursado virtual");
    }

    @Test
    @DisplayName("release: expande los espejos de la principal con el mismo motivo y nota, y los borra y libera juntos")
    void releaseExpandeEspejos() {
        ReleaseCommand command = releaseCommand(10L);
        OccurrenceSlotDto principal = occurrenceSlot(10L, 1L, futureDate(1));
        OccurrenceSlotDto mirror = occurrenceSlot(11L, 1L, futureDate(1));
        when(targetResolver.resolveAll(command.targets(), LocalDate.now())).thenReturn(List.of(principal));
        when(occurrenceService.findMirrors(Set.of(10L))).thenReturn(List.of(mirror));
        when(allocationRepository.findByOccurrenceIdIn(List.of(10L, 11L))).thenReturn(List.of(
                allocation(100L, 10L, 5, AllocationSource.MANUAL), allocation(101L, 11L, 6, AllocationSource.MANUAL)));

        List<ReleasedOccurrenceDto> result = service.release(command);

        verify(writer).delete(List.of(principal, mirror));
        verify(occurrenceService).release(
                List.of(new OccurrenceRelease(10L, 5L), new OccurrenceRelease(11L, 6L)),
                ReleaseReason.VIRTUAL_CLASS, "cursado virtual");
        assertThat(result).extracting(ReleasedOccurrenceDto::occurrenceId).containsExactly(10L, 11L);
        assertThat(result).extracting(ReleasedOccurrenceDto::reason)
                .containsOnly(ReleaseReason.VIRTUAL_CLASS);
    }

    @Test
    @DisplayName("release: un espejo que ya venia en el pedido no se duplica")
    void releaseNoDuplicaEspejoPedidoExplicitamente() {
        ReleaseCommand command = releaseCommand(10L, 11L);
        OccurrenceSlotDto principal = occurrenceSlot(10L, 1L, futureDate(1));
        OccurrenceSlotDto mirror = occurrenceSlot(11L, 1L, futureDate(1));
        when(targetResolver.resolveAll(command.targets(), LocalDate.now())).thenReturn(List.of(principal, mirror));
        when(occurrenceService.findMirrors(Set.of(10L, 11L))).thenReturn(List.of(mirror));

        service.release(command);

        verify(writer).delete(List.of(principal, mirror));
        verify(occurrenceService).release(
                List.of(new OccurrenceRelease(10L, null), new OccurrenceRelease(11L, null)),
                ReleaseReason.VIRTUAL_CLASS, "cursado virtual");
    }

    @Test
    @DisplayName("release: guarda el aula que tenia asignada como aula anterior y la devuelve como freedClassroomId")
    void releaseGuardaElAulaAnterior() {
        ReleaseCommand command = releaseCommand(10L);
        OccurrenceSlotDto occ = occurrenceSlot(10L, 1L, futureDate(1));
        when(targetResolver.resolveAll(command.targets(), LocalDate.now())).thenReturn(List.of(occ));
        when(allocationRepository.findByOccurrenceIdIn(List.of(10L)))
                .thenReturn(List.of(allocation(100L, 10L, 5, AllocationSource.MANUAL)));

        List<ReleasedOccurrenceDto> result = service.release(command);

        ArgumentCaptor<List<OccurrenceRelease>> captor = ArgumentCaptor.forClass(List.class);
        verify(occurrenceService).release(captor.capture(), eq(ReleaseReason.VIRTUAL_CLASS), eq("cursado virtual"));
        assertThat(captor.getValue()).containsExactly(new OccurrenceRelease(10L, 5L));
        assertThat(result).containsExactly(new ReleasedOccurrenceDto(10L, 1L, occ.date(), 5L, ReleaseReason.VIRTUAL_CLASS));
    }

    @Test
    @DisplayName("release: borra la asignacion antes de marcar la ocurrencia como liberada")
    void releaseBorraAntesDeLiberar() {
        ReleaseCommand command = releaseCommand(10L);
        OccurrenceSlotDto occ = occurrenceSlot(10L, 1L, futureDate(1));
        when(targetResolver.resolveAll(command.targets(), LocalDate.now())).thenReturn(List.of(occ));

        service.release(command);

        InOrder inOrder = inOrder(writer, occurrenceService);
        inOrder.verify(writer).delete(List.of(occ));
        inOrder.verify(occurrenceService).release(anyList(), eq(ReleaseReason.VIRTUAL_CLASS), eq("cursado virtual"));
    }

    @Test
    @DisplayName("release: sin alcance sobre el edificio del aula asignada -> AccessDeniedException, no borra ni libera")
    void releaseSinAlcanceNoHaceNada() {
        ReleaseCommand command = releaseCommand(10L);
        OccurrenceSlotDto occ = occurrenceSlot(10L, 1L, futureDate(1));
        when(targetResolver.resolveAll(command.targets(), LocalDate.now())).thenReturn(List.of(occ));
        when(allocationRepository.findByOccurrenceIdIn(List.of(10L)))
                .thenReturn(List.of(allocation(100L, 10L, 5, AllocationSource.MANUAL)));
        when(classroomService.findByIdsIncludingDeactivated(Set.of(5L)))
                .thenReturn(List.of(classroomIn(5L, 9L, "Edificio Ajeno")));
        doThrow(new AccessDeniedException("sin acceso"))
                .when(buildingScopeResolver).requireAccess(Permission.ALLOCATION_WRITE, Set.of(9L));

        assertThatThrownBy(() -> service.release(command)).isInstanceOf(AccessDeniedException.class);

        verify(writer, never()).delete(any());
        verify(occurrenceService, never()).release(any(), any(), any());
    }

    @Test
    @DisplayName("release: sin aula actual ni anterior (D13) pasa sin validar alcance y libera con aula anterior nula")
    void releaseSinAulaActualNiAnteriorPasa() {
        ReleaseCommand command = releaseCommand(10L);
        OccurrenceSlotDto occ = occurrenceSlot(10L, 1L, futureDate(1));
        when(targetResolver.resolveAll(command.targets(), LocalDate.now())).thenReturn(List.of(occ));

        List<ReleasedOccurrenceDto> result = service.release(command);

        verify(buildingScopeResolver, never()).requireAccess(any(Permission.class), anyCollection());
        verify(occurrenceService).release(List.of(new OccurrenceRelease(10L, null)),
                ReleaseReason.VIRTUAL_CLASS, "cursado virtual");
        assertThat(result).extracting(ReleasedOccurrenceDto::freedClassroomId).containsExactly((Long) null);
    }

    @Test
    @DisplayName("release: sin aula actual valida el alcance contra el aula anterior y la conserva")
    void releaseUsaAulaAnteriorCuandoNoHayActual() {
        ReleaseCommand command = releaseCommand(10L);
        OccurrenceSlotDto occ = slotWithPreviousClassroom(10L, 7L);
        when(targetResolver.resolveAll(command.targets(), LocalDate.now())).thenReturn(List.of(occ));
        when(classroomService.findByIdsIncludingDeactivated(Set.of(7L)))
                .thenReturn(List.of(classroomIn(7L, 9L, "Edificio Ajeno")));
        doThrow(new AccessDeniedException("sin acceso"))
                .when(buildingScopeResolver).requireAccess(Permission.ALLOCATION_WRITE, Set.of(9L));

        assertThatThrownBy(() -> service.release(command)).isInstanceOf(AccessDeniedException.class);
        verify(occurrenceService, never()).release(any(), any(), any());
    }

    @Test
    @DisplayName("release: sin aula actual y con alcance sobre la anterior, la reescribe como aula anterior")
    void releaseConservaAulaAnteriorSinAulaActual() {
        ReleaseCommand command = releaseCommand(10L);
        OccurrenceSlotDto occ = slotWithPreviousClassroom(10L, 7L);
        when(targetResolver.resolveAll(command.targets(), LocalDate.now())).thenReturn(List.of(occ));
        when(classroomService.findByIdsIncludingDeactivated(Set.of(7L)))
                .thenReturn(List.of(classroomIn(7L, 9L, "Edificio Propio")));

        service.release(command);

        verify(buildingScopeResolver).requireAccess(Permission.ALLOCATION_WRITE, Set.of(9L));
        verify(occurrenceService).release(List.of(new OccurrenceRelease(10L, 7L)),
                ReleaseReason.VIRTUAL_CLASS, "cursado virtual");
    }

    @Test
    @DisplayName("release: ocurrencia pasada -> el validator corta, no borra ni libera")
    void releaseOcurrenciaPasadaNoHaceNada() {
        ReleaseCommand command = releaseCommand(10L);
        OccurrenceSlotDto occ = occurrenceSlot(10L, 1L, LocalDate.now().minusDays(1));
        when(targetResolver.resolveAll(command.targets(), LocalDate.now())).thenReturn(List.of(occ));
        doThrow(new AllocationConflictException("ya ocurrió")).when(validator).validateNotPast(occ);

        assertThatThrownBy(() -> service.release(command)).isInstanceOf(AllocationConflictException.class);

        verify(writer, never()).delete(any());
        verify(occurrenceService, never()).release(any(), any(), any());
    }

    // ---------- cancelRelease ----------

    @Test
    @DisplayName("cancelRelease: no revierte espejos (ni lapidas) aunque la principal este en el conjunto")
    void cancelReleaseExcluyeEspejos() {
        DeallocationCommand command = new DeallocationCommand(List.of(new AllocationTarget.Event(1L)), null);
        when(targetResolver.resolveAll(command.targets(), LocalDate.now()))
                .thenReturn(List.of(releasedSlot(10L, null), releasedSlot(11L, 10L)));

        List<Long> result = service.cancelRelease(command);

        assertThat(result).containsExactly(10L);
        verify(occurrenceService).requestRoom(List.of(10L));
    }

    @Test
    @DisplayName("cancelRelease: un espejo pedido explicitamente por occurrenceIds tampoco se revierte")
    void cancelReleaseNoRevierteEspejoPedidoExplicitamente() {
        DeallocationCommand command = new DeallocationCommand(List.of(new AllocationTarget.Occurrences(List.of(11L))), null);
        when(targetResolver.resolveAll(command.targets(), LocalDate.now())).thenReturn(List.of(releasedSlot(11L, 10L)));

        List<Long> result = service.cancelRelease(command);

        assertThat(result).isEmpty();
        verify(occurrenceService).requestRoom(List.of());
    }

    private OccurrenceSlotDto releasedSlot(long id, Long mirrorOf) {
        return new OccurrenceSlotDto(id, 1L, futureDate(1), LocalTime.of(8, 0), LocalTime.of(9, 30),
                OccurrenceStatus.ROOM_RELEASED, 30, null, null, null, mirrorOf);
    }

    // ---------- deallocate ----------

    @Test
    @DisplayName("deallocate: target Occurrences, batch de 1 → valida no-pasado y libera")
    void deallocateOccurrencesTargetBatchDeUno() {
        DeallocationCommand command = new DeallocationCommand(List.of(new AllocationTarget.Occurrences(List.of(10L))), "obs");
        OccurrenceSlotDto occ = occurrenceSlot(10L, 1L, futureDate(1));
        when(targetResolver.resolveAll(command.targets(), null)).thenReturn(List.of(occ));
        when(writer.delete(List.of(occ))).thenReturn(List.of(new AllocationWriter.DeallocatedOccurrence(10L, 5L)));

        List<DeallocatedOccurrenceDto> result = service.deallocate(command);

        assertThat(result).containsExactly(new DeallocatedOccurrenceDto(10L, 5L));
        verify(validator).validateNotPast(occ);
        verify(writer).delete(List.of(occ));
    }

    @Test
    @DisplayName("deallocate: target Event, batch de N → valida y libera todas las occurrences resueltas")
    void deallocateEventTargetBatchDeN() {
        DeallocationCommand command = new DeallocationCommand(List.of(new AllocationTarget.Event(1L)), "obs");
        OccurrenceSlotDto occ1 = occurrenceSlot(10L, 1L, futureDate(1));
        OccurrenceSlotDto occ2 = occurrenceSlot(11L, 1L, futureDate(8));
        when(targetResolver.resolveAll(command.targets(), null)).thenReturn(List.of(occ1, occ2));
        when(writer.delete(List.of(occ1, occ2))).thenReturn(List.of(
                new AllocationWriter.DeallocatedOccurrence(10L, 5L),
                new AllocationWriter.DeallocatedOccurrence(11L, 6L)));

        List<DeallocatedOccurrenceDto> result = service.deallocate(command);

        assertThat(result).containsExactly(new DeallocatedOccurrenceDto(10L, 5L), new DeallocatedOccurrenceDto(11L, 6L));
        verify(validator).validateNotPast(occ1);
        verify(validator).validateNotPast(occ2);
    }

    @Test
    @DisplayName("deallocate: occurrence ya pasada → el validator corta, writer.delete nunca se llama")
    void deallocateOcurrenciaPasadaNoLibera() {
        DeallocationCommand command = new DeallocationCommand(List.of(new AllocationTarget.Occurrences(List.of(10L))), "obs");
        OccurrenceSlotDto occ = occurrenceSlot(10L, 1L, LocalDate.now().minusDays(1));
        when(targetResolver.resolveAll(command.targets(), null)).thenReturn(List.of(occ));
        doThrow(new AllocationConflictException("ya ocurrió")).when(validator).validateNotPast(occ);

        assertThatThrownBy(() -> service.deallocate(command)).isInstanceOf(AllocationConflictException.class);

        verify(writer, never()).delete(any());
    }

    @Test
    @DisplayName("deallocate: aula actual fuera del alcance del usuario → AccessDeniedException, no libera")
    void deallocateSinAlcanceSobreElEdificioActualNoLibera() {
        DeallocationCommand command = new DeallocationCommand(List.of(new AllocationTarget.Occurrences(List.of(10L))), "obs");
        OccurrenceSlotDto occ = occurrenceSlot(10L, 1L, futureDate(1));
        when(targetResolver.resolveAll(command.targets(), null)).thenReturn(List.of(occ));
        when(allocationRepository.findByOccurrenceIdIn(List.of(10L)))
                .thenReturn(List.of(allocation(100L, 10L, 5, AllocationSource.MANUAL)));
        ClassroomResponseDto classroom = new ClassroomResponseDto(5L, 101, 40, 9L, "Edificio Ajeno", 1L, "Normal", null, true);
        when(classroomService.findByIdsIncludingDeactivated(Set.of(5L))).thenReturn(List.of(classroom));
        doThrow(new AccessDeniedException("sin acceso"))
                .when(buildingScopeResolver).requireAccess(Permission.ALLOCATION_WRITE, Set.of(9L));

        assertThatThrownBy(() -> service.deallocate(command)).isInstanceOf(AccessDeniedException.class);

        verify(writer, never()).delete(any());
    }

    @Test
    @DisplayName("deallocate: sin occurrences que liberar (occurrences sin asignación se ignoran) → lista vacía")
    void deallocateSinOccurrencesEsNoOp() {
        DeallocationCommand command = new DeallocationCommand(List.of(new AllocationTarget.Occurrences(List.of(10L))), "obs");
        when(targetResolver.resolveAll(command.targets(), null)).thenReturn(List.of());
        when(writer.delete(List.of())).thenReturn(List.of());

        List<DeallocatedOccurrenceDto> result = service.deallocate(command);

        assertThat(result).isEmpty();
        verifyNoInteractions(validator);
    }

    // ---------- syncFromSysacad ----------

    @Test
    @DisplayName("syncFromSysacad: clampea a null (no descarta ocurrencias pasadas, plan §4) y delega el mapa a writer.syncFromSysacad")
    void syncFromSysacadClampeaNullYDelegaAlWriter() {
        AllocationItem item = new AllocationItem(new AllocationTarget.Event(1L), 5L);
        OccurrenceSlotDto pastOcc = occurrenceSlot(10L, 1L, LocalDate.now().minusMonths(2));
        Map<OccurrenceSlotDto, Long> resolved = mapOf(pastOcc, 5);
        when(targetResolver.resolveClassroomByOccurrence(List.of(item), null)).thenReturn(assignments(resolved));
        when(writer.syncFromSysacad(resolved)).thenReturn(1);

        int affected = service.syncFromSysacad(List.of(item));

        assertThat(affected).isEqualTo(1);
        verify(targetResolver).resolveClassroomByOccurrence(List.of(item), null);
        verify(writer).syncFromSysacad(resolved);
    }

    @Test
    @DisplayName("syncFromSysacad: las liberadas se saltean (el writer no las ve) y se loguean en WARN")
    void syncFromSysacadSalteaLiberadasYLoguea() {
        AllocationItem item = new AllocationItem(new AllocationTarget.Event(1L), 5L);
        OccurrenceSlotDto libre = occurrenceSlot(10L, 1L, futureDate(1));
        OccurrenceSlotDto liberada = new OccurrenceSlotDto(11L, 1L, futureDate(8), LocalTime.of(8, 0),
                LocalTime.of(9, 30), OccurrenceStatus.ROOM_RELEASED, 30,
                ar.edu.utn.frc.siga.events.model.ReleaseReason.VIRTUAL_CLASS, null, null, null);
        Map<OccurrenceSlotDto, Long> resolved = mapOf(libre, 5);
        when(targetResolver.resolveClassroomByOccurrence(List.of(item), null))
                .thenReturn(new AllocationTargetResolver.ResolvedAssignments(resolved, List.of(liberada)));
        when(writer.syncFromSysacad(resolved)).thenReturn(1);
        ch.qos.logback.classic.Logger logger =
                (ch.qos.logback.classic.Logger) org.slf4j.LoggerFactory.getLogger(AllocationServiceImpl.class);
        ch.qos.logback.core.read.ListAppender<ch.qos.logback.classic.spi.ILoggingEvent> appender =
                new ch.qos.logback.core.read.ListAppender<>();
        appender.start();
        logger.addAppender(appender);

        try {
            service.syncFromSysacad(List.of(item));
        } finally {
            logger.detachAppender(appender);
        }

        verify(writer).syncFromSysacad(resolved);
        assertThat(appender.list).anySatisfy(e -> {
            assertThat(e.getLevel()).isEqualTo(ch.qos.logback.classic.Level.WARN);
            assertThat(e.getFormattedMessage()).contains("liberadas salteadas").contains("11");
        });
    }

    @Test
    @DisplayName("syncFromSysacad: valida que las aulas propuestas existan, igual que allocate/reallocate")
    void syncFromSysacadValidaAulasDisponibles() {
        AllocationItem item = new AllocationItem(new AllocationTarget.Event(1L), 5L);
        OccurrenceSlotDto occ = occurrenceSlot(10L, 1L, futureDate(1));
        Map<OccurrenceSlotDto, Long> resolved = mapOf(occ, 5);
        when(targetResolver.resolveClassroomByOccurrence(List.of(item), null)).thenReturn(assignments(resolved));
        when(writer.syncFromSysacad(resolved)).thenReturn(1);

        service.syncFromSysacad(List.of(item));

        verify(validator).validateClassroomsAvailable(Set.of(5L));
    }

    @Test
    @DisplayName("syncFromSysacad: source SYSACAD no valida solapamiento (se importa tal cual, supuestos §3)")
    void syncFromSysacadNoValidaSolapamiento() {
        AllocationItem item = new AllocationItem(new AllocationTarget.Event(1L), 5L);
        OccurrenceSlotDto occ = occurrenceSlot(10L, 1L, futureDate(1));
        Map<OccurrenceSlotDto, Long> resolved = mapOf(occ, 5);
        when(targetResolver.resolveClassroomByOccurrence(List.of(item), null)).thenReturn(assignments(resolved));
        when(writer.syncFromSysacad(resolved)).thenReturn(1);

        service.syncFromSysacad(List.of(item));

        verify(validator, never()).validateManualOverlap(anyList(), any());
    }

    @Test
    @DisplayName("syncFromSysacad: toma el lock por aula y no llama a ningún método de overlap del validator")
    void syncFromSysacadTomaElLockYNoValidaSolapamiento() {
        AllocationItem item = new AllocationItem(new AllocationTarget.Event(1L), 5L);
        OccurrenceSlotDto occ = occurrenceSlot(10L, 1L, futureDate(1));
        Map<OccurrenceSlotDto, Long> resolved = mapOf(occ, 5);
        when(targetResolver.resolveClassroomByOccurrence(List.of(item), null)).thenReturn(assignments(resolved));
        when(writer.syncFromSysacad(resolved)).thenReturn(1);

        service.syncFromSysacad(List.of(item));

        verify(classroomLock).lock(Set.of(5L));
        verify(validator, never()).validateManualOverlap(anyList(), any());
        verify(validator, never()).validateStrictOverlap(anyList());
    }

    @Test
    @DisplayName("syncFromSysacad: lote que no resuelve a ninguna ocurrencia es un no-op (sin validar aulas)")
    void syncFromSysacadTargetVacioEsNoOp() {
        AllocationItem item = new AllocationItem(new AllocationTarget.Event(1L), 5L);
        when(targetResolver.resolveClassroomByOccurrence(List.of(item), null)).thenReturn(assignments(Map.of()));
        when(writer.syncFromSysacad(Map.of())).thenReturn(0);

        int affected = service.syncFromSysacad(List.of(item));

        assertThat(affected).isEqualTo(0);
        verify(validator, never()).validateClassroomsAvailable(any());
    }

    // ---------- resolveOccurrences ----------

    @Test
    @DisplayName("resolveOccurrences: delega en targetResolver.resolveAll con hoy como clamp y devuelve lo que resuelve")
    void resolveOccurrencesDelegaEnTargetResolver() {
        AllocationTarget target = new AllocationTarget.Occurrences(List.of(10L));
        OccurrenceSlotDto occ = occurrenceSlot(10L, 1L, futureDate(1));
        when(targetResolver.resolveAll(List.of(target), LocalDate.now())).thenReturn(List.of(occ));

        List<OccurrenceSlotDto> result = service.resolveOccurrences(target);

        assertThat(result).containsExactly(occ);
        verify(targetResolver).resolveAll(List.of(target), LocalDate.now());
    }

    // ---------- findById / findByDate ----------

    @Test
    @DisplayName("findById: existente → compone el dto")
    void findByIdExistente() {
        Allocation alloc = allocation(1L, 10L, 5, AllocationSource.MANUAL);
        when(allocationRepository.findById(1L)).thenReturn(Optional.of(alloc));
        when(composer.compose(alloc)).thenReturn(dummyResponseDto());

        AllocationResponseDto result = service.findById(1L);

        assertThat(result.id()).isEqualTo(1L);
    }

    @Test
    @DisplayName("findById: inexistente → ResourceNotFoundException")
    void findByIdInexistente() {
        when(allocationRepository.findById(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.findById(1L)).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("findByDate: resuelve occurrences de la fecha y compone sus asignaciones")
    void findByDateComponeAsignaciones() {
        LocalDate date = futureDate(1);
        OccurrenceSlotDto occ = occurrenceSlot(10L, 1L, date);
        Allocation alloc = allocation(1L, 10L, 5, AllocationSource.MANUAL);
        when(occurrenceService.findSlotsByDate(date)).thenReturn(List.of(occ));
        when(allocationRepository.findByOccurrenceIdIn(List.of(10L))).thenReturn(List.of(alloc));
        when(composer.composeAll(List.of(alloc))).thenReturn(List.of(dummyResponseDto()));

        List<AllocationResponseDto> result = service.findByDate(date);

        assertThat(result).hasSize(1);
    }

    @Test
    @DisplayName("findById: aula fuera del alcance de lectura → ResourceNotFoundException, no AccessDeniedException")
    void findByIdFueraDeAlcanceEsNotFound() {
        Allocation alloc = allocation(1L, 10L, 5, AllocationSource.MANUAL);
        ClassroomResponseDto classroom = new ClassroomResponseDto(5L, 101, 40, 9L, "Edificio Ajeno", 1L, "Normal", null, true);
        when(allocationRepository.findById(1L)).thenReturn(Optional.of(alloc));
        when(composer.compose(alloc)).thenReturn(responseDtoWithClassroom(classroom));
        when(buildingScopeResolver.scopeFor(Permission.ALLOCATION_READ)).thenReturn(BuildingScope.of(Set.of(1L)));

        assertThatThrownBy(() -> service.findById(1L)).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("findByDate: filtra las asignaciones cuyo edificio está fuera del alcance de lectura")
    void findByDateFiltraFueraDeAlcance() {
        LocalDate date = futureDate(1);
        OccurrenceSlotDto occ = occurrenceSlot(10L, 1L, date);
        Allocation alloc = allocation(1L, 10L, 5, AllocationSource.MANUAL);
        ClassroomResponseDto classroom = new ClassroomResponseDto(5L, 101, 40, 9L, "Edificio Ajeno", 1L, "Normal", null, true);
        when(occurrenceService.findSlotsByDate(date)).thenReturn(List.of(occ));
        when(allocationRepository.findByOccurrenceIdIn(List.of(10L))).thenReturn(List.of(alloc));
        when(composer.composeAll(List.of(alloc))).thenReturn(List.of(responseDtoWithClassroom(classroom)));
        when(buildingScopeResolver.scopeFor(Permission.ALLOCATION_READ)).thenReturn(BuildingScope.of(Set.of(1L)));

        List<AllocationResponseDto> result = service.findByDate(date);

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("findByOccurrenceIds: sin ids, no consulta el repositorio y devuelve vacío")
    void findByOccurrenceIdsSinIds() {
        List<AllocationResponseDto> result = service.findByOccurrenceIds(List.of());

        assertThat(result).isEmpty();
        verifyNoInteractions(allocationRepository);
    }

    @Test
    @DisplayName("findByOccurrenceIds: compone las asignaciones de las ocurrencias dadas, sin filtrar por alcance de edificio")
    void findByOccurrenceIdsComponeSinFiltrarAlcance() {
        Allocation alloc = allocation(1L, 10L, 5, AllocationSource.MANUAL);
        ClassroomResponseDto classroom = new ClassroomResponseDto(5L, 101, 40, 9L, "Edificio Ajeno", 1L, "Normal", null, true);
        when(allocationRepository.findByOccurrenceIdIn(List.of(10L))).thenReturn(List.of(alloc));
        when(composer.composeAll(List.of(alloc))).thenReturn(List.of(responseDtoWithClassroom(classroom)));

        List<AllocationResponseDto> result = service.findByOccurrenceIds(List.of(10L));

        assertThat(result).hasSize(1);
        verifyNoInteractions(buildingScopeResolver);
    }

    // ---------- findClassroomIdByOccurrence ----------

    @Test
    @DisplayName("findClassroomIdByOccurrence: occurrence asignada → el classroomId de esa asignación")
    void findClassroomIdByOccurrenceAsignada() {
        Allocation alloc = allocation(1L, 10L, 5, AllocationSource.MANUAL);
        when(allocationRepository.findByOccurrenceId(10L)).thenReturn(Optional.of(alloc));

        Optional<Long> result = service.findClassroomIdByOccurrence(10L);

        assertThat(result).contains(5L);
    }

    @Test
    @DisplayName("findClassroomIdByOccurrence: occurrence sin asignación → Optional vacío")
    void findClassroomIdByOccurrenceSinAsignar() {
        when(allocationRepository.findByOccurrenceId(10L)).thenReturn(Optional.empty());

        Optional<Long> result = service.findClassroomIdByOccurrence(10L);

        assertThat(result).isEmpty();
    }

    // ---------- audit descriptions ----------

    private static final String CENTRAL = "Edificio Central";

    private AuditDescriptionProbe probe;
    private AllocationServiceImpl audited;

    private void auditedService() {
        probe = new AuditDescriptionProbe();
        audited = probe.audited(service);
    }

    private ClassroomResponseDto classroomIn(long id, long buildingId, String buildingName) {
        return new ClassroomResponseDto(id, 100 + (int) id, 40, buildingId, buildingName, 1L, "Normal", null, true);
    }

    /** Resolves {@code resolved} for a manual command and records the description seen when the writer runs. */
    private AllocationCommand stubManualWrite(Map<OccurrenceSlotDto, Long> resolved, boolean upsert) {
        AllocationItem item = new AllocationItem(new AllocationTarget.Event(1L), 5L);
        AllocationCommand command = AllocationCommand.manual(List.of(item), "obs");
        when(targetResolver.resolveClassroomByOccurrence(eq(command.items()), eq(LocalDate.now()))).thenReturn(assignments(resolved));
        if (upsert) {
            when(writer.upsert(eq(resolved), any(), any())).thenAnswer(inv -> {
                probe.peek();
                return List.of();
            });
        } else {
            when(writer.create(eq(resolved), any(), any())).thenAnswer(inv -> {
                probe.peek();
                return List.of();
            });
        }
        return command;
    }

    @Test
    @DisplayName("allocate: 1 aula en un edificio → 'Asignación de 1 aula, edificio X' (singular)")
    void allocateDescribesSingularClassroom() {
        auditedService();
        Map<OccurrenceSlotDto, Long> resolved = mapOf(occurrenceSlot(10L, 1L, futureDate(1)), 5);
        AllocationCommand command = stubManualWrite(resolved, false);
        when(classroomService.findByIdsIncludingDeactivated(Set.of(5L))).thenReturn(List.of(classroomIn(5L, 1L, CENTRAL)));

        audited.allocate(command);

        assertThat(probe.peeked()).isEqualTo("Asignación de 1 aula, edificio Edificio Central");
    }

    @Test
    @DisplayName("allocate: 2 aulas del mismo edificio → 'Asignación de 2 aulas, edificio X' (plural)")
    void allocateDescribesPluralClassrooms() {
        auditedService();
        Map<OccurrenceSlotDto, Long> resolved = mapOf(
                occurrenceSlot(10L, 1L, futureDate(1)), 5, occurrenceSlot(11L, 1L, futureDate(2)), 6);
        AllocationCommand command = stubManualWrite(resolved, false);
        when(classroomService.findByIdsIncludingDeactivated(Set.of(5L, 6L)))
                .thenReturn(List.of(classroomIn(5L, 1L, CENTRAL), classroomIn(6L, 1L, CENTRAL)));

        audited.allocate(command);

        assertThat(probe.peeked()).isEqualTo("Asignación de 2 aulas, edificio Edificio Central");
    }

    @Test
    @DisplayName("allocate: varias ocurrencias en la misma aula cuentan 1 aula")
    void allocateCountsDistinctClassrooms() {
        auditedService();
        Map<OccurrenceSlotDto, Long> resolved = mapOf(
                occurrenceSlot(10L, 1L, futureDate(1)), 5, occurrenceSlot(11L, 1L, futureDate(2)), 5);
        AllocationCommand command = stubManualWrite(resolved, false);
        when(classroomService.findByIdsIncludingDeactivated(Set.of(5L))).thenReturn(List.of(classroomIn(5L, 1L, CENTRAL)));

        audited.allocate(command);

        assertThat(probe.peeked()).isEqualTo("Asignación de 1 aula, edificio Edificio Central");
    }

    @Test
    @DisplayName("allocate: aulas de dos edificios → 'Asignación de 2 aulas, 2 edificios' sin nombrar edificio")
    void allocateDescribesSeveralBuildings() {
        auditedService();
        Map<OccurrenceSlotDto, Long> resolved = mapOf(
                occurrenceSlot(10L, 1L, futureDate(1)), 5, occurrenceSlot(11L, 1L, futureDate(2)), 6);
        AllocationCommand command = stubManualWrite(resolved, false);
        when(classroomService.findByIdsIncludingDeactivated(Set.of(5L, 6L)))
                .thenReturn(List.of(classroomIn(5L, 1L, CENTRAL), classroomIn(6L, 2L, "Edificio Norte")));

        audited.allocate(command);

        assertThat(probe.peeked()).isEqualTo("Asignación de 2 aulas, 2 edificios");
    }

    @Test
    @DisplayName("reallocate: 2 aulas del mismo edificio → 'Reasignación de 2 aulas, edificio X'")
    void reallocateDescribesClassroomsAndBuilding() {
        auditedService();
        Map<OccurrenceSlotDto, Long> resolved = mapOf(
                occurrenceSlot(10L, 1L, futureDate(1)), 5, occurrenceSlot(11L, 1L, futureDate(2)), 6);
        AllocationCommand command = stubManualWrite(resolved, true);
        when(classroomService.findByIdsIncludingDeactivated(Set.of(5L, 6L)))
                .thenReturn(List.of(classroomIn(5L, 1L, CENTRAL), classroomIn(6L, 1L, CENTRAL)));

        audited.reallocate(command);

        assertThat(probe.peeked()).isEqualTo("Reasignación de 2 aulas, edificio Edificio Central");
    }

    @Test
    @DisplayName("allocate y reallocate sin ocurrencias resueltas conservan el texto fijo de @AuditOperation")
    void emptyBatchKeepsFixedDescription() {
        auditedService();
        AllocationCommand command = stubManualWrite(Map.of(), false);
        audited.allocate(command);
        assertThat(probe.peeked()).isEqualTo("Asignación de aulas en lote");

        AllocationCommand reCommand = stubManualWrite(Map.of(), true);
        audited.reallocate(reCommand);
        assertThat(probe.peeked()).isEqualTo("Reasignación de aulas en lote");
    }

    @Test
    @DisplayName("deallocate: 1 ocurrencia → 'Liberación de 1 ocurrencia' (singular)")
    void deallocateDescribesSingularOccurrence() {
        auditedService();
        DeallocationCommand command = new DeallocationCommand(List.of(new AllocationTarget.Occurrences(List.of(10L))), "obs");
        OccurrenceSlotDto occ = occurrenceSlot(10L, 1L, futureDate(1));
        when(targetResolver.resolveAll(command.targets(), null)).thenReturn(List.of(occ));
        when(writer.delete(List.of(occ))).thenAnswer(inv -> {
            probe.peek();
            return List.of();
        });

        audited.deallocate(command);

        assertThat(probe.peeked()).isEqualTo("Liberación de 1 ocurrencia");
    }

    @Test
    @DisplayName("deallocate: 2 ocurrencias → 'Liberación de 2 ocurrencias' (plural)")
    void deallocateDescribesPluralOccurrences() {
        auditedService();
        DeallocationCommand command = new DeallocationCommand(List.of(new AllocationTarget.Event(1L)), "obs");
        OccurrenceSlotDto occ1 = occurrenceSlot(10L, 1L, futureDate(1));
        OccurrenceSlotDto occ2 = occurrenceSlot(11L, 1L, futureDate(8));
        when(targetResolver.resolveAll(command.targets(), null)).thenReturn(List.of(occ1, occ2));
        when(writer.delete(List.of(occ1, occ2))).thenAnswer(inv -> {
            probe.peek();
            return List.of();
        });

        audited.deallocate(command);

        assertThat(probe.peeked()).isEqualTo("Liberación de 2 ocurrencias");
    }

    @ParameterizedTest
    @CsvSource({"0,0 afectadas", "1,1 afectada", "2,2 afectadas"})
    @DisplayName("syncFromSysacad: la descripción lleva el conteo con la concordancia correcta")
    void syncFromSysacadDescribesAffected(int affected, String expectedCount) {
        auditedService();
        AllocationItem item = new AllocationItem(new AllocationTarget.Event(1L), 5L);
        when(targetResolver.resolveClassroomByOccurrence(List.of(item), null)).thenReturn(assignments(Map.of()));
        when(writer.syncFromSysacad(Map.of())).thenAnswer(inv -> {
            AuditDescriptionProbe.stamp();
            return affected;
        });

        audited.syncFromSysacad(List.of(item));

        assertThat(probe.rewrittenDescription())
                .isEqualTo("Sincronización de asignaciones desde SysAcad: " + expectedCount);
    }

    // ---------- helpers ----------

    private static AllocationTargetResolver.ResolvedAssignments assignments(Map<OccurrenceSlotDto, Long> m) {
        return new AllocationTargetResolver.ResolvedAssignments(m, List.of());
    }

    private OccurrenceSlotDto occurrenceSlot(long id, long eventId, LocalDate date) {
        return new OccurrenceSlotDto(id, eventId, date, LocalTime.of(8, 0), LocalTime.of(9, 30), OccurrenceStatus.NEEDS_ROOM, 30, null, null, null, null);
    }

    private Allocation allocation(long id, Long occurrenceId, long classroomId, AllocationSource source) {
        return Allocation.builder()
                .id(id)
                .occurrenceId(occurrenceId)
                .classroomId(classroomId)
                .source(source)
                .build();
    }

    private AllocationResponseDto dummyResponseDto() {
        return new AllocationResponseDto(1L, AllocationSource.MANUAL, Instant.now(), null, null, null, null);
    }

    private AllocationResponseDto responseDtoWithClassroom(ClassroomResponseDto classroom) {
        return new AllocationResponseDto(1L, AllocationSource.MANUAL, Instant.now(), null, null, null, classroom);
    }

    private Map<OccurrenceSlotDto, Long> mapOf(OccurrenceSlotDto occ, long classroomId) {
        Map<OccurrenceSlotDto, Long> map = new LinkedHashMap<>();
        map.put(occ, classroomId);
        return map;
    }

    private Map<OccurrenceSlotDto, Long> mapOf(OccurrenceSlotDto occ1, long classroomId1, OccurrenceSlotDto occ2, long classroomId2) {
        Map<OccurrenceSlotDto, Long> map = new LinkedHashMap<>();
        map.put(occ1, classroomId1);
        map.put(occ2, classroomId2);
        return map;
    }

    private Map<OccurrenceSlotDto, Long> mapOf(OccurrenceSlotDto occ1, long classroomId1, OccurrenceSlotDto occ2, long classroomId2,
            OccurrenceSlotDto occ3, long classroomId3) {
        Map<OccurrenceSlotDto, Long> map = new LinkedHashMap<>();
        map.put(occ1, classroomId1);
        map.put(occ2, classroomId2);
        map.put(occ3, classroomId3);
        return map;
    }

    private LocalDate futureDate(int daysFromNow) {
        return LocalDate.now().plusDays(daysFromNow);
    }
}
