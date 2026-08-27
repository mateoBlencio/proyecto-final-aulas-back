package ar.edu.utn.frc.siga.preview.mapper;

import ar.edu.utn.frc.siga.allocation.validator.OccupiedSlot;
import ar.edu.utn.frc.siga.events.dto.response.AcademicEventResponseDto;
import ar.edu.utn.frc.siga.events.dto.response.RecurringEventResponseDto;
import ar.edu.utn.frc.siga.optimizer.model.OptimizationResult;
import ar.edu.utn.frc.siga.optimizer.model.OptimizerAllocation;
import ar.edu.utn.frc.siga.preview.dto.response.PreviewItemDto;
import ar.edu.utn.frc.siga.preview.dto.response.PreviewResponseDto;
import ar.edu.utn.frc.siga.preview.dto.response.RoomStretchDto;
import ar.edu.utn.frc.siga.preview.validator.PreviewValidator;
import ar.edu.utn.frc.siga.space.dto.response.ClassroomResponseDto;
import ar.edu.utn.frc.siga.space.service.ClassroomService;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.when;

/**
 * Unit tests del aviso "este evento hoy no tiene una sola aula" que arma {@link PreviewComposer}.
 *
 * <p>Es la información que el motor descartaba: su modelo asume un evento = un aula, así que
 * confirmar una propuesta colapsa a un aula única cualquier movimiento temporal que hubiera.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("PreviewComposer — tramos de aula actuales")
class PreviewComposerRoomStretchesTest {

    private static final long EVENT_ID = 55L;
    private static final LocalDate MONDAY = LocalDate.of(2026, 3, 3);

    @Mock
    private ClassroomService classroomService;
    @Mock
    private PreviewValidator previewValidator;

    private PreviewComposer composer;

    @BeforeEach
    void setUp() {
        composer = new PreviewComposer(classroomService, previewValidator);
        when(classroomService.findByIds(anyCollection())).thenReturn(List.of(
                classroom(3), classroom(12), classroom(20)));
    }

    @Test
    @DisplayName("evento partido en dos aulas → dos tramos, con bordes y cantidad de clases")
    void eventoPartidoDevuelveTramos() {
        List<OccupiedSlot> slots = List.of(
                slot(MONDAY, 3),
                slot(MONDAY.plusWeeks(1), 12),
                slot(MONDAY.plusWeeks(2), 12),
                slot(MONDAY.plusWeeks(3), 3));

        List<RoomStretchDto> stretches = stretchesOf(slots);

        assertThat(stretches).hasSize(3);
        assertThat(stretches.get(0))
                .extracting(s -> s.classroom().id(), RoomStretchDto::from, RoomStretchDto::to, RoomStretchDto::classes)
                .containsExactly(3L, MONDAY, MONDAY, 1);
        assertThat(stretches.get(1))
                .extracting(s -> s.classroom().id(), RoomStretchDto::from, RoomStretchDto::to, RoomStretchDto::classes)
                .containsExactly(12L, MONDAY.plusWeeks(1), MONDAY.plusWeeks(2), 2);
        assertThat(stretches.get(2))
                .extracting(s -> s.classroom().id(), RoomStretchDto::from, RoomStretchDto::to, RoomStretchDto::classes)
                .containsExactly(3L, MONDAY.plusWeeks(3), MONDAY.plusWeeks(3), 1);
    }

    @Test
    @DisplayName("el orden de llegada no importa: los tramos se arman por fecha")
    void ordenDeLlegadaNoImporta() {
        List<OccupiedSlot> desordenados = List.of(
                slot(MONDAY.plusWeeks(2), 12),
                slot(MONDAY, 3),
                slot(MONDAY.plusWeeks(1), 12));

        List<RoomStretchDto> stretches = stretchesOf(desordenados);

        assertThat(stretches).hasSize(2);
        assertThat(stretches.get(0).classroom().id()).isEqualTo(3L);
        assertThat(stretches.get(1).classroom().id()).isEqualTo(12L);
        assertThat(stretches.get(1).classes()).isEqualTo(2);
    }

    @Test
    @DisplayName("evento con una sola aula → lista vacía: no hay nada que avisar")
    void unaSolaAulaNoAvisa() {
        List<OccupiedSlot> slots = List.of(slot(MONDAY, 3), slot(MONDAY.plusWeeks(1), 3));

        assertThat(stretchesOf(slots)).isEmpty();
    }

    @Test
    @DisplayName("evento sin asignaciones → lista vacía")
    void sinAsignacionesNoAvisa() {
        assertThat(stretchesOf(List.of())).isEmpty();
    }

    @Test
    @DisplayName("el evento vuelve al aula de origen → tres tramos, no dos")
    void vuelveAlAulaDeOrigen() {
        List<OccupiedSlot> slots = List.of(
                slot(MONDAY, 3L),
                slot(MONDAY.plusWeeks(1), 20L),
                slot(MONDAY.plusWeeks(2), 3L));

        List<RoomStretchDto> stretches = stretchesOf(slots);

        assertThat(stretches).extracting(s -> s.classroom().id()).containsExactly(3L, 20L, 3L);
    }

    // ---------- helpers ----------

    /** Compone una propuesta mínima de un solo evento y devuelve sus tramos actuales. */
    private List<RoomStretchDto> stretchesOf(List<OccupiedSlot> ownSlots) {
        OptimizerAllocation proposal = new OptimizerAllocation(String.valueOf(EVENT_ID), 20L);
        PreviewResponseDto response = composer.compose(
                new OptimizationResult("preview-1", List.of(proposal)),
                List.<RecurringEventResponseDto>of(),
                Map.of(EVENT_ID, List.of(MONDAY)),
                Map.of(EVENT_ID, 3L),
                ownSlots.isEmpty() ? Map.of() : Map.of(EVENT_ID, ownSlots),
                List.of(),
                List.of());

        assertThat(response.allocations()).hasSize(1);
        return response.allocations().getFirst().currentRoomStretches();
    }

    private static OccupiedSlot slot(LocalDate date, long classroomId) {
        return new OccupiedSlot(classroomId, date, LocalTime.of(8, 0), LocalTime.of(10, 0), EVENT_ID, 1L);
    }

    private static ClassroomResponseDto classroom(long id) {
        return new ClassroomResponseDto(id, (int) id, 40, 1L, "Edificio", 1L, "Aula común");
    }
}
