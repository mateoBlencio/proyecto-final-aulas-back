package ar.edu.utn.frc.siga.preview.mapper;

import ar.edu.utn.frc.siga.allocation.validator.OccupiedSlot;
import ar.edu.utn.frc.siga.events.dto.response.AcademicEventResponseDto;
import ar.edu.utn.frc.siga.events.dto.response.RecurringEventResponseDto;
import ar.edu.utn.frc.siga.events.dto.response.UniqueEventResponseDto;
import ar.edu.utn.frc.siga.events.model.EventType;
import ar.edu.utn.frc.siga.events.model.UniqueEventKind;
import ar.edu.utn.frc.siga.optimizer.model.OptimizationResult;
import ar.edu.utn.frc.siga.optimizer.model.OptimizerAllocation;
import ar.edu.utn.frc.siga.optimizer.model.OptimizerRoom;
import ar.edu.utn.frc.siga.preview.dto.response.MoveConflictDto.ConflictOrigin;
import ar.edu.utn.frc.siga.preview.dto.response.PreviewItemDto;
import ar.edu.utn.frc.siga.preview.dto.response.PreviewResponseDto;
import ar.edu.utn.frc.siga.preview.dto.response.UnmetRequirementDto;
import ar.edu.utn.frc.siga.preview.dto.response.UnmetRequirementDto.RequirementSeverity;
import ar.edu.utn.frc.siga.preview.dto.response.UnmetRequirementDto.RequirementType;
import ar.edu.utn.frc.siga.preview.service.impl.PlanningKey;
import ar.edu.utn.frc.siga.preview.validator.PreviewValidator;
import ar.edu.utn.frc.siga.space.dto.response.ClassroomResponseDto;
import ar.edu.utn.frc.siga.space.model.ClassroomResources;
import ar.edu.utn.frc.siga.space.service.ClassroomService;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("PreviewComposer - eventos únicos, conflictos y requisitos")
class PreviewComposerTest {

    private static final LocalDate DATE = LocalDate.of(2026, 11, 10);
    private static final LocalTime START = LocalTime.of(18, 0);
    private static final long DURATION = 120;
    private static final long RECURRING_ID = 1L;
    private static final long UNIQUE_ID = 3L;

    @Mock
    private ClassroomService classroomService;

    private PreviewComposer composer;

    @BeforeEach
    void setUp() {
        composer = new PreviewComposer(classroomService, new PreviewValidator());
        when(classroomService.findByIdsIncludingDeactivated(anyCollection())).thenReturn(List.of(
                classroom(5L, 40), classroom(6L, 40)));
        when(classroomService.findResourceAmounts(anyString(), anyCollection())).thenReturn(Map.of());
    }

    @Test
    @DisplayName("un evento sin resolver bloqueado por un único ya resuelto trae conflicto PREVIEW con el id del único")
    void eventoSinResolverBloqueadoPorUnicoResuelto() {
        RecurringEventResponseDto blocked = recurring(RECURRING_ID);
        UniqueEventResponseDto unique = unique(UNIQUE_ID, 20, false, false, null, null, null);
        PlanningKey blockedKey = new PlanningKey(RECURRING_ID, 1);
        PlanningKey uniqueKey = new PlanningKey(UNIQUE_ID, 1);

        PreviewResponseDto response = composer.compose(
                result(new OptimizerAllocation("1", null), new OptimizerAllocation("3", 5L)),
                List.of(blocked, unique),
                Map.of(blockedKey, List.of(DATE), uniqueKey, List.of(DATE)),
                Map.of(), Map.of(), List.of(room(5L)), List.of());

        assertThat(response.allocations()).singleElement()
                .satisfies(item -> assertThat(item.event().id()).isEqualTo(UNIQUE_ID));
        assertThat(response.unresolved()).singleElement().satisfies(item -> {
            assertThat(item.event().id()).isEqualTo(RECURRING_ID);
            assertThat(item.conflicts()).singleElement().satisfies(conflict -> {
                assertThat(conflict.origin()).isEqualTo(ConflictOrigin.PREVIEW);
                assertThat(conflict.conflictingEventId()).isEqualTo(UNIQUE_ID);
                assertThat(conflict.classroomId()).isEqualTo(5L);
                assertThat(conflict.date()).isEqualTo(DATE);
            });
        });
    }

    @Test
    @DisplayName("un único sin resolver devuelve conflictos no vacíos, con el evento que ocupa el aula")
    void unicoSinResolverTraeConflictos() {
        UniqueEventResponseDto unique = unique(UNIQUE_ID, 20, false, false, null, null, null);
        OccupiedSlot blocker = new OccupiedSlot(5L, DATE, START, START.plusMinutes(DURATION), 99L, 500L);

        PreviewResponseDto response = composer.compose(
                result(new OptimizerAllocation("3", null)),
                List.of(unique),
                Map.of(new PlanningKey(UNIQUE_ID, 1), List.of(DATE)),
                Map.of(), Map.of(), List.of(room(5L)), List.of(blocker));

        assertThat(response.allocations()).isEmpty();
        assertThat(response.unresolved()).singleElement().satisfies(item -> {
            assertThat(item.event().id()).isEqualTo(UNIQUE_ID);
            assertThat(item.roomSlot()).isEqualTo(1);
            assertThat(item.dates()).containsExactly(DATE);
            assertThat(item.conflicts()).singleElement().satisfies(conflict -> {
                assertThat(conflict.origin()).isEqualTo(ConflictOrigin.DATABASE);
                assertThat(conflict.conflictingEventId()).isEqualTo(99L);
            });
        });
    }

    @Test
    @DisplayName("requiere proyector y el aula no tiene: PROJECTOR / MINOR")
    void proyectorFaltanteEsMinor() {
        UniqueEventResponseDto unique = unique(UNIQUE_ID, 20, true, false, null, null, null);

        assertThat(unmetOf(unique, 1, 5L)).containsExactly(
                new UnmetRequirementDto(RequirementType.PROJECTOR, RequirementSeverity.MINOR));
    }

    @Test
    @DisplayName("requiere proyector y el aula tiene uno: sin advertencias")
    void proyectorPresenteNoAdvierte() {
        when(classroomService.findResourceAmounts(eq(ClassroomResources.PROJECTOR), anyCollection()))
                .thenReturn(Map.of(5L, 1));
        UniqueEventResponseDto unique = unique(UNIQUE_ID, 20, true, false, null, null, null);

        assertThat(unmetOf(unique, 1, 5L)).isEmpty();
    }

    @Test
    @DisplayName("PCs: el aula con menos de ceil(computerCount / n) es COMPUTERS / SEVERE; con exactamente ese valor no advierte")
    void computadorasInsuficientesSonSevere() {
        UniqueEventResponseDto unique = unique(UNIQUE_ID, 60, false, true, 30, null, null);

        when(classroomService.findResourceAmounts(eq(ClassroomResources.COMPUTERS), anyCollection()))
                .thenReturn(Map.of(5L, 14, 6L, 15));

        assertThat(unmetOf(unique, 2, 5L)).containsExactly(
                new UnmetRequirementDto(RequirementType.COMPUTERS, RequirementSeverity.SEVERE));
        assertThat(unmetOf(unique, 2, 6L)).isEmpty();
    }

    @Test
    @DisplayName("software y usuarios de examen se informan como UNVERIFIABLE aunque el aula tenga PCs de sobra")
    void softwareYUsuariosDeExamenSonNoVerificables() {
        when(classroomService.findResourceAmounts(eq(ClassroomResources.COMPUTERS), anyCollection()))
                .thenReturn(Map.of(5L, 100));
        UniqueEventResponseDto unique = unique(UNIQUE_ID, 20, false, true, 10, "Matlab", true);

        assertThat(unmetOf(unique, 1, 5L)).containsExactly(
                new UnmetRequirementDto(RequirementType.SOFTWARE, RequirementSeverity.UNVERIFIABLE),
                new UnmetRequirementDto(RequirementType.EXAM_USERS, RequirementSeverity.UNVERIFIABLE));
    }

    @Test
    @DisplayName("un recurrente no tiene advertencias y no consulta recursos del aula")
    void recurrenteSinAdvertencias() {
        PreviewResponseDto response = composer.compose(
                result(new OptimizerAllocation("1", 5L)),
                List.of(recurring(RECURRING_ID)),
                Map.of(new PlanningKey(RECURRING_ID, 1), List.of(DATE)),
                Map.of(), Map.of(), List.of(room(5L)), List.of());

        assertThat(response.allocations()).singleElement()
                .satisfies(item -> assertThat(item.unmetRequirements()).isEmpty());
        verify(classroomService, never()).findResourceAmounts(anyString(), anyCollection());
    }

    @Test
    @DisplayName("overcrowdedBy usa los inscriptos repartidos: 100 inscriptos en 2 aulas de 40 sobran 10 por aula")
    void sobrecupoConInscriptosRepartidos() {
        UniqueEventResponseDto unique = unique(UNIQUE_ID, 100, false, false, null, null, null);

        PreviewResponseDto response = composeUniqueInSlots(unique, 2, 5L);

        assertThat(response.allocations()).extracting(PreviewItemDto::roomSlot).containsExactly(1, 2);
        assertThat(response.allocations()).extracting(PreviewItemDto::overcrowdedBy).containsOnly(10);
    }

    @Test
    @DisplayName("overcrowdedBy sin espejos usa todos los inscriptos")
    void sobrecupoSinEspejos() {
        UniqueEventResponseDto unique = unique(UNIQUE_ID, 100, false, false, null, null, null);

        PreviewResponseDto response = composeUniqueInSlots(unique, 1, 5L);

        assertThat(response.allocations()).singleElement()
                .satisfies(item -> assertThat(item.overcrowdedBy()).isEqualTo(60));
    }

    @Test
    @DisplayName("espejo parcial de un recurrente: la clave 1 usa todos los inscriptos y la clave 2 la mitad")
    void espejoParcialRepartePorClave() {
        RecurringEventResponseDto event = new RecurringEventResponseDto(RECURRING_ID, EventType.RECURRING, 100, START,
                DURATION, DayOfWeek.TUESDAY, DATE, DATE.plusWeeks(1), null, null);
        PlanningKey main = new PlanningKey(RECURRING_ID, 1);
        PlanningKey mirror = new PlanningKey(RECURRING_ID, 2);

        PreviewResponseDto response = composer.compose(
                result(new OptimizerAllocation("1", 5L), new OptimizerAllocation("1:2", 6L)),
                List.of(event),
                Map.of(main, List.of(DATE, DATE.plusWeeks(1)), mirror, List.of(DATE)),
                Map.of(), Map.of(), List.of(room(5L), room(6L)), List.of());

        assertThat(response.allocations()).extracting(PreviewItemDto::roomSlot).containsExactly(1, 2);
        assertThat(response.allocations()).extracting(PreviewItemDto::overcrowdedBy).containsExactly(60, 10);
    }

    private List<UnmetRequirementDto> unmetOf(UniqueEventResponseDto unique, int slots, long roomId) {
        PreviewResponseDto response = composeUniqueInSlots(unique, slots, roomId);
        return response.allocations().getFirst().unmetRequirements();
    }

    private PreviewResponseDto composeUniqueInSlots(UniqueEventResponseDto unique, int slots, long roomId) {
        List<OptimizerAllocation> allocations = new ArrayList<>();
        Map<PlanningKey, List<LocalDate>> dates = new LinkedHashMap<>();
        for (int slot = 1; slot <= slots; slot++) {
            PlanningKey key = new PlanningKey(unique.id(), slot);
            allocations.add(new OptimizerAllocation(key.toPlanningId(), roomId));
            dates.put(key, List.of(DATE));
        }
        return composer.compose(new OptimizationResult("prev_x", allocations), List.<AcademicEventResponseDto>of(unique),
                dates, Map.of(), Map.of(), List.of(room(roomId)), List.of());
    }

    private static OptimizationResult result(OptimizerAllocation... allocations) {
        return new OptimizationResult("prev_x", List.of(allocations));
    }

    private static OptimizerRoom room(long id) {
        return new OptimizerRoom(id, 40, 1L);
    }

    private static RecurringEventResponseDto recurring(long id) {
        return new RecurringEventResponseDto(id, EventType.RECURRING, 30, START, DURATION,
                DayOfWeek.TUESDAY, DATE, DATE, null, null);
    }

    private static UniqueEventResponseDto unique(long id, int enrolled, boolean projector, boolean computers,
            Integer computerCount, String software, Boolean examUsers) {
        return new UniqueEventResponseDto(id, EventType.UNIQUE_EVENT, UniqueEventKind.PARCIAL, enrolled, START,
                DURATION, DATE, null, null, null, projector, computers, computerCount, software, examUsers);
    }

    private static ClassroomResponseDto classroom(long id, int capacity) {
        return new ClassroomResponseDto(id, (int) id, capacity, 1L, "Edificio", 1L, "Aula común", null, true);
    }
}
