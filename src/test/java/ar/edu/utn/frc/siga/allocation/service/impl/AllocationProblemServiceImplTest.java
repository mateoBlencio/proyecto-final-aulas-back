package ar.edu.utn.frc.siga.allocation.service.impl;

import ar.edu.utn.frc.siga.academic.dto.response.AcademicPeriodResponseDto;
import ar.edu.utn.frc.siga.academic.service.AcademicPeriodService;
import ar.edu.utn.frc.siga.allocation.events.dto.response.AcademicEventResponseDto;
import ar.edu.utn.frc.siga.allocation.dto.response.ClassroomOverlapDto;
import ar.edu.utn.frc.siga.allocation.dto.response.OvercrowdedAllocationDto;
import ar.edu.utn.frc.siga.allocation.events.dto.response.RecurringEventResponseDto;
import ar.edu.utn.frc.siga.allocation.events.mapper.AcademicEventComposer;
import ar.edu.utn.frc.siga.allocation.events.model.AcademicEvent;
import ar.edu.utn.frc.siga.allocation.model.Allocation;
import ar.edu.utn.frc.siga.allocation.model.AllocationSource;
import ar.edu.utn.frc.siga.allocation.events.model.EventType;
import ar.edu.utn.frc.siga.allocation.events.model.Occurrence;
import ar.edu.utn.frc.siga.allocation.events.model.OccurrenceStatus;
import ar.edu.utn.frc.siga.allocation.events.model.RecurringEvent;
import ar.edu.utn.frc.siga.allocation.repository.AllocationRepository;
import ar.edu.utn.frc.siga.allocation.events.service.AcademicEventService;
import ar.edu.utn.frc.siga.common.exception.InvalidDateRangeException;
import ar.edu.utn.frc.siga.space.dto.response.ClassroomResponseDto;
import ar.edu.utn.frc.siga.space.service.ClassroomService;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("AllocationProblemServiceImpl")
class AllocationProblemServiceImplTest {

    private static final Pageable PAGEABLE = PageRequest.of(0, 20);

    @Mock
    private AllocationRepository allocationRepository;
    @Mock
    private ClassroomService classroomService;
    @Mock
    private AcademicEventService academicEventService;
    @Mock
    private AcademicPeriodService academicPeriodService;
    @Mock
    private AcademicEventComposer academicEventComposer;

    @InjectMocks
    private AllocationProblemServiceImpl service;

    @BeforeEach
    void setUp() {
        lenient().when(allocationRepository.findOccupancyBetween(any(), any(), any())).thenReturn(List.of());
        lenient().when(academicEventService.findUnassignedEvents(any(), any(), anyBoolean())).thenReturn(List.of());
        lenient().when(academicPeriodService.findActive()).thenReturn(List.of());
        lenient().when(classroomService.findByIds(any())).thenReturn(List.of());
        lenient().when(academicEventComposer.composeById(ArgumentMatchers.<List<? extends AcademicEvent>>any()))
                .thenAnswer(invocation -> {
            List<AcademicEvent> events = invocation.getArgument(0);
            Map<Long, AcademicEventResponseDto> result = new LinkedHashMap<>();
            for (AcademicEvent event : events) {
                result.put(event.getId(), new RecurringEventResponseDto(event.getId(), EventType.RECURRING, event.getEnrolled(),
                        event.getStartTime(), event.getDuration().toMinutes(), null, null, null, null, null));
            }
            return result;
        });
    }

    @Test
    @DisplayName("Detecta sobrecupo cuando los inscriptos superan la capacidad del aula")
    void detectaSobrecupo() {
        LocalDate from = futureDate(0);
        LocalDate to = futureDate(30);
        RecurringEvent event = recurringEvent(1L, 40, LocalTime.of(8, 0), 60);
        Allocation allocation = allocation(100L, occurrence(10L, event, futureDate(2)), 5);

        when(allocationRepository.findOccupancyBetween(from, to, OccurrenceStatus.ASSIGNED))
                .thenReturn(List.of(allocation));
        when(classroomService.findByIds(any())).thenReturn(List.of(classroom(5, 30)));

        List<OvercrowdedAllocationDto> result = service.findOvercrowded(from, to, false, PAGEABLE).getContent();

        assertThat(result).hasSize(1);
        OvercrowdedAllocationDto overcrowded = result.getFirst();
        assertThat(overcrowded.enrolled()).isEqualTo(40);
        assertThat(overcrowded.capacity()).isEqualTo(30);
        assertThat(overcrowded.excess()).isEqualTo(10);
        assertThat(overcrowded.dates()).containsExactly(futureDate(2));
    }

    @Test
    @DisplayName("No detecta sobrecupo cuando los inscriptos no superan la capacidad")
    void noDetectaSobrecupoCuandoHayLugar() {
        LocalDate from = futureDate(0);
        LocalDate to = futureDate(30);
        RecurringEvent event = recurringEvent(1L, 20, LocalTime.of(8, 0), 60);
        Allocation allocation = allocation(100L, occurrence(10L, event, futureDate(2)), 5);

        when(allocationRepository.findOccupancyBetween(from, to, OccurrenceStatus.ASSIGNED))
                .thenReturn(List.of(allocation));
        when(classroomService.findByIds(any())).thenReturn(List.of(classroom(5, 30)));

        assertThat(service.findOvercrowded(from, to, false, PAGEABLE)).isEmpty();
    }

    @Test
    @DisplayName("No detecta sobrecupo cuando 'enrolled' es null (se trata como 0)")
    void noDetectaSobrecupoConEnrolledNull() {
        LocalDate from = futureDate(0);
        LocalDate to = futureDate(30);
        RecurringEvent event = recurringEvent(1L, null, LocalTime.of(8, 0), 60);
        Allocation allocation = allocation(100L, occurrence(10L, event, futureDate(2)), 5);

        when(allocationRepository.findOccupancyBetween(from, to, OccurrenceStatus.ASSIGNED))
                .thenReturn(List.of(allocation));
        when(classroomService.findByIds(any())).thenReturn(List.of(classroom(5, 30)));

        assertThat(service.findOvercrowded(from, to, false, PAGEABLE)).isEmpty();
    }

    @Test
    @DisplayName("Detecta superposición cuando dos eventos comparten aula y fecha con horarios cruzados")
    void detectaSolapeMismaAulaMismaFecha() {
        LocalDate from = futureDate(0);
        LocalDate to = futureDate(30);
        LocalDate date = futureDate(2);
        RecurringEvent eventA = recurringEvent(1L, 10, LocalTime.of(8, 0), 60);
        RecurringEvent eventB = recurringEvent(2L, 10, LocalTime.of(8, 30), 60);
        Allocation allocA = allocation(100L, occurrence(10L, eventA, date), 5);
        Allocation allocB = allocation(101L, occurrence(11L, eventB, date), 5);

        when(allocationRepository.findOccupancyBetween(from, to, OccurrenceStatus.ASSIGNED))
                .thenReturn(List.of(allocA, allocB));
        when(classroomService.findByIds(any())).thenReturn(List.of(classroom(5, 100)));

        List<ClassroomOverlapDto> result = service.findOverlaps(from, to, false, PAGEABLE).getContent();

        assertThat(result).hasSize(1);
        ClassroomOverlapDto overlap = result.getFirst();
        assertThat(overlap.classroom().id()).isEqualTo(5);
        assertThat(overlap.eventA().id()).isEqualTo(1L);
        assertThat(overlap.eventB().id()).isEqualTo(2L);
        assertThat(overlap.dates()).containsExactly(date);
    }

    @Test
    @DisplayName("No detecta superposición cuando los eventos están en aulas distintas")
    void noDetectaSolapeEnAulasDistintas() {
        LocalDate from = futureDate(0);
        LocalDate to = futureDate(30);
        LocalDate date = futureDate(2);
        RecurringEvent eventA = recurringEvent(1L, 10, LocalTime.of(8, 0), 60);
        RecurringEvent eventB = recurringEvent(2L, 10, LocalTime.of(8, 30), 60);
        Allocation allocA = allocation(100L, occurrence(10L, eventA, date), 5);
        Allocation allocB = allocation(101L, occurrence(11L, eventB, date), 6);

        when(allocationRepository.findOccupancyBetween(from, to, OccurrenceStatus.ASSIGNED))
                .thenReturn(List.of(allocA, allocB));
        when(classroomService.findByIds(any())).thenReturn(List.of(classroom(5, 100), classroom(6, 100)));

        assertThat(service.findOverlaps(from, to, false, PAGEABLE)).isEmpty();
    }

    @Test
    @DisplayName("No detecta superposición cuando los eventos son en fechas distintas")
    void noDetectaSolapeEnFechasDistintas() {
        LocalDate from = futureDate(0);
        LocalDate to = futureDate(30);
        RecurringEvent eventA = recurringEvent(1L, 10, LocalTime.of(8, 0), 60);
        RecurringEvent eventB = recurringEvent(2L, 10, LocalTime.of(8, 30), 60);
        Allocation allocA = allocation(100L, occurrence(10L, eventA, futureDate(2)), 5);
        Allocation allocB = allocation(101L, occurrence(11L, eventB, futureDate(3)), 5);

        when(allocationRepository.findOccupancyBetween(from, to, OccurrenceStatus.ASSIGNED))
                .thenReturn(List.of(allocA, allocB));
        when(classroomService.findByIds(any())).thenReturn(List.of(classroom(5, 100)));

        assertThat(service.findOverlaps(from, to, false, PAGEABLE)).isEmpty();
    }

    @Test
    @DisplayName("No detecta superposición cuando las franjas son adyacentes (fin de una = inicio de la otra)")
    void noDetectaSolapeEnFranjasAdyacentes() {
        LocalDate from = futureDate(0);
        LocalDate to = futureDate(30);
        LocalDate date = futureDate(2);
        RecurringEvent eventA = recurringEvent(1L, 10, LocalTime.of(8, 0), 60);
        RecurringEvent eventB = recurringEvent(2L, 10, LocalTime.of(9, 0), 60);
        Allocation allocA = allocation(100L, occurrence(10L, eventA, date), 5);
        Allocation allocB = allocation(101L, occurrence(11L, eventB, date), 5);

        when(allocationRepository.findOccupancyBetween(from, to, OccurrenceStatus.ASSIGNED))
                .thenReturn(List.of(allocA, allocB));
        when(classroomService.findByIds(any())).thenReturn(List.of(classroom(5, 100)));

        assertThat(service.findOverlaps(from, to, false, PAGEABLE)).isEmpty();
    }

    @Test
    @DisplayName("Agrega todas las fechas de un par recurrente en conflicto en una sola fila")
    void agregaFechasDeParRecurrenteEnUnaFila() {
        LocalDate from = futureDate(0);
        LocalDate to = futureDate(30);
        LocalDate date1 = futureDate(2);
        LocalDate date2 = futureDate(9);
        RecurringEvent eventA = recurringEvent(1L, 10, LocalTime.of(8, 0), 60);
        RecurringEvent eventB = recurringEvent(2L, 10, LocalTime.of(8, 30), 60);

        Allocation allocA1 = allocation(100L, occurrence(10L, eventA, date1), 5);
        Allocation allocB1 = allocation(101L, occurrence(11L, eventB, date1), 5);
        Allocation allocA2 = allocation(102L, occurrence(12L, eventA, date2), 5);
        Allocation allocB2 = allocation(103L, occurrence(13L, eventB, date2), 5);

        when(allocationRepository.findOccupancyBetween(from, to, OccurrenceStatus.ASSIGNED))
                .thenReturn(List.of(allocA1, allocB1, allocA2, allocB2));
        when(classroomService.findByIds(any())).thenReturn(List.of(classroom(5, 100)));

        List<ClassroomOverlapDto> result = service.findOverlaps(from, to, false, PAGEABLE).getContent();

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().dates()).containsExactly(date1, date2);
    }

    @Test
    @DisplayName("Usa el fin del período académico activo como 'to' por defecto")
    void usaPeriodoActivoComoToPorDefecto() {
        LocalDate endDate = LocalDate.now().plusDays(50);
        when(academicPeriodService.findActive()).thenReturn(List.of(
                new AcademicPeriodResponseDto(2026, 2, LocalDate.now().minusDays(10), endDate)));

        service.findOvercrowded(null, null, false, PAGEABLE);

        ArgumentCaptor<LocalDate> toCaptor = ArgumentCaptor.forClass(LocalDate.class);
        verify(allocationRepository).findOccupancyBetween(any(), toCaptor.capture(), eq(OccurrenceStatus.ASSIGNED));
        assertThat(toCaptor.getValue()).isEqualTo(endDate);
    }

    @Test
    @DisplayName("Usa 'from' + 6 meses como fallback cuando no hay período académico activo")
    void usaFallbackSeisMesesSinPeriodoActivo() {
        LocalDate from = LocalDate.of(2026, 8, 1);

        service.findOvercrowded(from, null, false, PAGEABLE);

        ArgumentCaptor<LocalDate> toCaptor = ArgumentCaptor.forClass(LocalDate.class);
        verify(allocationRepository).findOccupancyBetween(eq(from), toCaptor.capture(), eq(OccurrenceStatus.ASSIGNED));
        assertThat(toCaptor.getValue()).isEqualTo(from.plusMonths(6));
    }

    @Test
    @DisplayName("Lanza InvalidDateRangeException cuando 'to' es anterior a 'from'")
    void lanzaExcepcionCuandoToEsAnteriorAFrom() {
        LocalDate from = LocalDate.of(2026, 8, 10);
        LocalDate to = LocalDate.of(2026, 8, 1);

        assertThatThrownBy(() -> service.findOvercrowded(from, to, false, PAGEABLE))
                .isInstanceOf(InvalidDateRangeException.class);
    }

    @Test
    @DisplayName("findUnassigned delega en findUnassignedEvents con el 'to' por defecto ya resuelto")
    void findUnassignedDelegaConToResuelto() {
        LocalDate from = LocalDate.of(2026, 8, 1);

        service.findUnassigned(from, null, false, PAGEABLE);

        ArgumentCaptor<LocalDate> toCaptor = ArgumentCaptor.forClass(LocalDate.class);
        verify(academicEventService).findUnassignedEvents(eq(from), toCaptor.capture(), eq(false));
        assertThat(toCaptor.getValue()).isEqualTo(from.plusMonths(6));
    }

    @Test
    @DisplayName("resolveAllUnassignedEventIds delega en findUnassignedEventIds con el rango por defecto")
    void resolveAllUnassignedEventIdsDelegaConRangoPorDefecto() {
        when(academicEventService.findUnassignedEventIds(any(), any(), eq(false))).thenReturn(List.of(1L, 2L));

        List<Long> ids = service.resolveAllUnassignedEventIds();

        assertThat(ids).containsExactly(1L, 2L);
        verify(academicEventService).findUnassignedEventIds(eq(LocalDate.now()), any(), eq(false));
    }

    /** Fecha relativa a hoy: evita que los tests de solapamiento/sobrecupo (isPast() filtra por reloj real) rompan con el paso del calendario. */
    private LocalDate futureDate(int daysFromNow) {
        return LocalDate.now().plusDays(daysFromNow);
    }

    private RecurringEvent recurringEvent(long id, Integer enrolled, LocalTime startTime, int durationMinutes) {
        return RecurringEvent.builder()
                .id(id)
                .enrolled(enrolled)
                .startTime(startTime)
                .duration(Duration.ofMinutes(durationMinutes))
                .dayOfWeek(DayOfWeek.MONDAY)
                .startDate(LocalDate.of(2026, 1, 1))
                .build();
    }

    private Occurrence occurrence(long id, AcademicEvent event, LocalDate date) {
        return Occurrence.builder()
                .id(id)
                .event(event)
                .date(date)
                .status(OccurrenceStatus.ASSIGNED)
                .build();
    }

    private Allocation allocation(long id, Occurrence occurrence, Integer classroomId) {
        return Allocation.builder()
                .id(id)
                .occurrence(occurrence)
                .classroomId(classroomId)
                .source(AllocationSource.MANUAL)
                .createdAt(LocalDateTime.now())
                .build();
    }

    private ClassroomResponseDto classroom(Integer id, Integer capacity) {
        return new ClassroomResponseDto(id, "Aula " + id, 1, capacity, true, 1, "Edificio 1", 1, "Tipo");
    }
}
