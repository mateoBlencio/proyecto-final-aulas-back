package ar.edu.utn.frc.siga.roomrequest.service.impl;

import ar.edu.utn.frc.siga.allocation.service.AllocationOccupancyService;
import ar.edu.utn.frc.siga.allocation.validator.OccupiedSlot;
import ar.edu.utn.frc.siga.auth.model.SystemRole;
import ar.edu.utn.frc.siga.auth.service.UserService;
import ar.edu.utn.frc.siga.common.exception.ResourceNotFoundException;
import ar.edu.utn.frc.siga.events.dto.response.OccurrenceSlotDto;
import ar.edu.utn.frc.siga.roomrequest.dto.response.AllowedClassroomDto;
import ar.edu.utn.frc.siga.roomrequest.exception.InvalidRoomRequestException;
import ar.edu.utn.frc.siga.roomrequest.dto.response.CandidateBuildingDto;
import ar.edu.utn.frc.siga.roomrequest.model.RoomRequestItem;
import ar.edu.utn.frc.siga.roomrequest.repository.RoomRequestItemRepository;
import ar.edu.utn.frc.siga.space.dto.response.ClassroomResponseDto;
import ar.edu.utn.frc.siga.space.service.ClassroomService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("RoomRequestCandidateResolver")
class RoomRequestCandidateResolverTest {

    private static final LocalDate MONDAY_1 = LocalDate.of(2026, 10, 12);
    private static final LocalDate MONDAY_2 = LocalDate.of(2026, 10, 19);
    private static final LocalDate MONDAY_3 = LocalDate.of(2026, 10, 26);

    @Mock
    private RoomRequestItemRepository itemRepository;
    @Mock
    private ClassroomService classroomService;
    @Mock
    private AllocationOccupancyService allocationOccupancyService;
    @Mock
    private UserService userService;
    @Mock
    private RoomRequestOccurrenceResolver occurrenceResolver;

    @InjectMocks
    private RoomRequestCandidateResolver resolver;

    @Test
    @DisplayName("findAllowedClassrooms: ítem inexistente → 404")
    void findAllowedClassrooms_itemInexistente() {
        when(itemRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> resolver.findAllowedClassrooms(99L))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("findAllowedClassrooms: aula ocupada en la fecha/horario sale con available=false, sin excluirse")
    void findAllowedClassrooms_aulaOcupada() {
        LocalDate date = LocalDate.of(2026, 3, 10);
        RoomRequestItem item = itemFor(date, LocalTime.of(10, 0), 60);
        when(itemRepository.findById(1L)).thenReturn(Optional.of(item));
        when(classroomService.findAllAvailable()).thenReturn(List.of(classroom(101L)));
        when(allocationOccupancyService.findOccupancy(date, date)).thenReturn(List.of(
                new OccupiedSlot(101L, date, LocalTime.of(10, 30), LocalTime.of(11, 30), 5L, 7L)));

        List<AllowedClassroomDto> result = resolver.findAllowedClassrooms(1L);

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().id()).isEqualTo(101L);
        assertThat(result.getFirst().available()).isFalse();
    }

    @Test
    @DisplayName("findAllowedClassrooms: requiresComputers filtra por el mínimo pedido")
    void findAllowedClassrooms_filtraPorComputadoras() {
        LocalDate date = LocalDate.of(2026, 3, 10);
        RoomRequestItem item = RoomRequestItem.builder()
                .date(date).startTime(LocalTime.of(10, 0)).duration(Duration.ofMinutes(60))
                .requiresComputers(true).computerCount(20).build();
        when(itemRepository.findById(1L)).thenReturn(Optional.of(item));
        when(classroomService.findAllAvailable()).thenReturn(List.of(classroom(101L), classroom(102L)));
        when(classroomService.findIdsWithResourceAtLeast("Cantidad de PC", 20)).thenReturn(Set.of(101L));
        when(allocationOccupancyService.findOccupancy(date, date)).thenReturn(List.of());

        List<AllowedClassroomDto> result = resolver.findAllowedClassrooms(1L);

        assertThat(result).extracting(AllowedClassroomDto::id).containsExactly(101L);
    }

    @Test
    @DisplayName("findAllowedClassrooms: requiresProjector filtra por ese recurso")
    void findAllowedClassrooms_filtraPorProyector() {
        LocalDate date = LocalDate.of(2026, 3, 10);
        RoomRequestItem item = RoomRequestItem.builder()
                .date(date).startTime(LocalTime.of(10, 0)).duration(Duration.ofMinutes(60))
                .requiresProjector(true).build();
        when(itemRepository.findById(1L)).thenReturn(Optional.of(item));
        when(classroomService.findAllAvailable()).thenReturn(List.of(classroom(101L), classroom(102L)));
        when(classroomService.findIdsWithResourceAtLeast("Proyector", 1)).thenReturn(Set.of(102L));
        when(allocationOccupancyService.findOccupancy(date, date)).thenReturn(List.of());

        List<AllowedClassroomDto> result = resolver.findAllowedClassrooms(1L);

        assertThat(result).extracting(AllowedClassroomDto::id).containsExactly(102L);
    }

    @Test
    @DisplayName("findAllowedClassrooms: sin aulas candidatas, devuelve lista vacía sin error")
    void findAllowedClassrooms_listaVacia() {
        LocalDate date = LocalDate.of(2026, 3, 10);
        RoomRequestItem item = itemFor(date, LocalTime.of(10, 0), 60);
        when(itemRepository.findById(1L)).thenReturn(Optional.of(item));
        when(classroomService.findAllAvailable()).thenReturn(List.of());
        when(allocationOccupancyService.findOccupancy(date, date)).thenReturn(List.of());

        assertThat(resolver.findAllowedClassrooms(1L)).isEmpty();
    }

    @Test
    @DisplayName("occupiedClassroomIds: cambio regular, aula ocupada todos los lunes → ocupada")
    void occupiedClassroomIds_regularOcupadaTodosLosDias() {
        RoomRequestItem item = regularItem();
        givenOwnMondays(item);
        when(allocationOccupancyService.findOccupancy(MONDAY_1, MONDAY_3)).thenReturn(List.of(
                busy(230L, MONDAY_1, "19:55", "22:10", 1L),
                busy(230L, MONDAY_2, "19:55", "22:10", 2L),
                busy(230L, MONDAY_3, "19:55", "22:10", 3L)));

        assertThat(resolver.occupiedClassroomIds(item)).containsExactly(230L);
    }

    @Test
    @DisplayName("occupiedClassroomIds: cambio regular, aula ocupada en un solo lunes → ocupada")
    void occupiedClassroomIds_regularOcupadaUnSoloDia() {
        RoomRequestItem item = regularItem();
        givenOwnMondays(item);
        when(allocationOccupancyService.findOccupancy(MONDAY_1, MONDAY_3)).thenReturn(List.of(
                busy(1L, MONDAY_2, "20:00", "21:00", 2L)));

        assertThat(resolver.occupiedClassroomIds(item)).containsExactly(1L);
    }

    @Test
    @DisplayName("occupiedClassroomIds: cambio regular, aula tomada solo por las ocurrencias propias → libre")
    void occupiedClassroomIds_regularSoloOcurrenciasPropias() {
        RoomRequestItem item = regularItem();
        givenOwnMondays(item);
        when(allocationOccupancyService.findOccupancy(MONDAY_1, MONDAY_3)).thenReturn(List.of(
                busy(215L, MONDAY_1, "19:55", "22:55", 100L),
                busy(215L, MONDAY_2, "19:55", "22:55", 101L),
                busy(215L, MONDAY_3, "19:55", "22:55", 102L)));

        assertThat(resolver.occupiedClassroomIds(item)).isEmpty();
    }

    @Test
    @DisplayName("occupiedClassroomIds: cambio regular, aula ocupada solo otro día de la semana → libre")
    void occupiedClassroomIds_regularOtroDiaDeLaSemana() {
        RoomRequestItem item = regularItem();
        givenOwnMondays(item);
        when(allocationOccupancyService.findOccupancy(MONDAY_1, MONDAY_3)).thenReturn(List.of(
                busy(230L, MONDAY_2.plusDays(1), "19:55", "22:10", 9L)));

        assertThat(resolver.occupiedClassroomIds(item)).isEmpty();
    }

    @Test
    @DisplayName("occupiedClassroomIds: pedido por fecha mira solo ese día y no resuelve días de dictado")
    void occupiedClassroomIds_pedidoPorFecha() {
        LocalDate date = LocalDate.of(2026, 10, 20);
        RoomRequestItem item = itemFor(date, LocalTime.of(18, 0), 120);
        when(allocationOccupancyService.findOccupancy(date, date)).thenReturn(List.of(
                busy(5L, date, "19:00", "20:00", 9L),
                busy(6L, date, "21:00", "22:00", 10L)));

        assertThat(resolver.occupiedClassroomIds(item)).containsExactly(5L);
        verifyNoInteractions(occurrenceResolver);
    }

    @Test
    @DisplayName("occupiedClassroomIds: cambio regular sin clases futuras propaga el error")
    void occupiedClassroomIds_regularSinClasesFuturas() {
        RoomRequestItem item = regularItem();
        when(occurrenceResolver.futureSlotsOnDayOfWeek(item))
                .thenThrow(new InvalidRoomRequestException("No quedan clases futuras"));

        assertThatThrownBy(() -> resolver.occupiedClassroomIds(item))
                .isInstanceOf(InvalidRoomRequestException.class);
        verifyNoInteractions(allocationOccupancyService);
    }

    @Test
    @DisplayName("findCandidateBuildings: edificio con las classroomCount aulas pedidas aparece")
    void findCandidateBuildings_edificioCompleto() {
        LocalDate date = LocalDate.of(2026, 3, 10);
        RoomRequestItem item = RoomRequestItem.builder()
                .date(date).startTime(LocalTime.of(10, 0)).duration(Duration.ofMinutes(60))
                .classroomCount(2).build();
        when(itemRepository.findById(1L)).thenReturn(Optional.of(item));
        when(classroomService.findAllAvailable()).thenReturn(
                List.of(classroom(101L, 1L, "Edificio Central"), classroom(102L, 1L, "Edificio Central")));
        when(allocationOccupancyService.findOccupancy(date, date)).thenReturn(List.of());
        when(userService.findBuildingIdsCoveredByRole(eq(SystemRole.AUXILIAR_AULICO), any()))
                .thenReturn(Set.of(1L));

        List<CandidateBuildingDto> result = resolver.findCandidateBuildings(1L);

        assertThat(result).containsExactly(new CandidateBuildingDto(1L, "Edificio Central", 2));
        verify(userService).findBuildingIdsCoveredByRole(SystemRole.AUXILIAR_AULICO, Set.of(1L));
    }

    @Test
    @DisplayName("findCandidateBuildings: edificio con solo 1 de 2 aulas libres también aparece")
    void findCandidateBuildings_resolucionParcial() {
        LocalDate date = LocalDate.of(2026, 3, 10);
        RoomRequestItem item = RoomRequestItem.builder()
                .date(date).startTime(LocalTime.of(10, 0)).duration(Duration.ofMinutes(60))
                .classroomCount(2).build();
        when(itemRepository.findById(1L)).thenReturn(Optional.of(item));
        when(classroomService.findAllAvailable()).thenReturn(
                List.of(classroom(101L, 1L, "Edificio Central"), classroom(102L, 1L, "Edificio Central")));
        when(allocationOccupancyService.findOccupancy(date, date)).thenReturn(List.of(
                new OccupiedSlot(102L, date, LocalTime.of(10, 0), LocalTime.of(11, 0), 5L, 7L)));
        when(userService.findBuildingIdsCoveredByRole(eq(SystemRole.AUXILIAR_AULICO), any()))
                .thenReturn(Set.of(1L));

        List<CandidateBuildingDto> result = resolver.findCandidateBuildings(1L);

        assertThat(result).containsExactly(new CandidateBuildingDto(1L, "Edificio Central", 1));
        verify(userService).findBuildingIdsCoveredByRole(SystemRole.AUXILIAR_AULICO, Set.of(1L));
    }

    @Test
    @DisplayName("findCandidateBuildings: edificio sin ninguna aula libre no aparece y no consulta auxiliares")
    void findCandidateBuildings_sinAulasLibres() {
        LocalDate date = LocalDate.of(2026, 3, 10);
        RoomRequestItem item = itemFor(date, LocalTime.of(10, 0), 60);
        when(itemRepository.findById(1L)).thenReturn(Optional.of(item));
        when(classroomService.findAllAvailable()).thenReturn(List.of(classroom(101L, 1L, "Edificio Central")));
        when(allocationOccupancyService.findOccupancy(date, date)).thenReturn(List.of(
                new OccupiedSlot(101L, date, LocalTime.of(10, 0), LocalTime.of(11, 0), 5L, 7L)));

        assertThat(resolver.findCandidateBuildings(1L)).isEmpty();
        verifyNoInteractions(userService);
    }

    @Test
    @DisplayName("findCandidateBuildings: edificio con aulas libres pero sin ningún auxiliar áulico asignado no aparece")
    void findCandidateBuildings_sinAuxiliarAsignado() {
        LocalDate date = LocalDate.of(2026, 3, 10);
        RoomRequestItem item = itemFor(date, LocalTime.of(10, 0), 60);
        when(itemRepository.findById(1L)).thenReturn(Optional.of(item));
        when(classroomService.findAllAvailable()).thenReturn(List.of(classroom(101L, 1L, "Edificio Central")));
        when(allocationOccupancyService.findOccupancy(date, date)).thenReturn(List.of());
        when(userService.findBuildingIdsCoveredByRole(eq(SystemRole.AUXILIAR_AULICO), any())).thenReturn(Set.of());

        assertThat(resolver.findCandidateBuildings(1L)).isEmpty();
    }

    private static RoomRequestItem regularItem() {
        return RoomRequestItem.builder()
                .dayOfWeek(DayOfWeek.MONDAY).startTime(LocalTime.of(19, 55)).duration(Duration.ofHours(3))
                .build();
    }

    private void givenOwnMondays(RoomRequestItem item) {
        when(occurrenceResolver.futureSlotsOnDayOfWeek(item)).thenReturn(List.of(
                ownSlot(100L, MONDAY_1), ownSlot(101L, MONDAY_2), ownSlot(102L, MONDAY_3)));
    }

    private static OccurrenceSlotDto ownSlot(Long occurrenceId, LocalDate date) {
        return new OccurrenceSlotDto(occurrenceId, 1L, date, LocalTime.of(19, 55), LocalTime.of(22, 55), null, null);
    }

    private static OccupiedSlot busy(Long classroomId, LocalDate date, String start, String end, Long occurrenceId) {
        return new OccupiedSlot(classroomId, date, LocalTime.parse(start), LocalTime.parse(end),
                9000L, 1L, occurrenceId);
    }

    private static RoomRequestItem itemFor(LocalDate date, LocalTime startTime, int durationMinutes) {
        return RoomRequestItem.builder()
                .date(date).startTime(startTime).duration(Duration.ofMinutes(durationMinutes))
                .build();
    }

    private static ClassroomResponseDto classroom(Long id) {
        return classroom(id, 1L, "Edificio Central");
    }

    private static ClassroomResponseDto classroom(Long id, Long buildingId, String buildingName) {
        return new ClassroomResponseDto(id, id.intValue(), 40, buildingId, buildingName, 1L, "Aula común");
    }
}
