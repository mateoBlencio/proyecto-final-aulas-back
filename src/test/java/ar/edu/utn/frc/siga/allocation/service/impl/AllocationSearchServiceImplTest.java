package ar.edu.utn.frc.siga.allocation.service.impl;

import ar.edu.utn.frc.siga.academic.dto.response.AcademicPeriodResponseDto;
import ar.edu.utn.frc.siga.academic.service.AcademicPeriodService;
import ar.edu.utn.frc.siga.allocation.dto.response.AllocationSearchOccurrenceDto;
import ar.edu.utn.frc.siga.allocation.dto.response.AllocationSearchResultDto;
import ar.edu.utn.frc.siga.allocation.model.AllocationSearchRow;
import ar.edu.utn.frc.siga.allocation.repository.AllocationSearchRepository;
import ar.edu.utn.frc.siga.common.exception.InvalidSelectionException;
import ar.edu.utn.frc.siga.common.security.BuildingScope;
import ar.edu.utn.frc.siga.common.security.BuildingScopeResolver;
import ar.edu.utn.frc.siga.common.security.Permission;
import ar.edu.utn.frc.siga.events.model.EventType;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("AllocationSearchServiceImpl")
class AllocationSearchServiceImplTest {

    private static final LocalDate DATE = LocalDate.of(2026, 9, 15);

    @Mock private AcademicPeriodService academicPeriodService;
    @Mock private BuildingScopeResolver buildingScopeResolver;
    @Mock private AllocationSearchRepository repository;

    private AllocationSearchServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new AllocationSearchServiceImpl(academicPeriodService, buildingScopeResolver, repository);
    }

    private static AcademicPeriodResponseDto period(LocalDate start, LocalDate end) {
        return new AcademicPeriodResponseDto(2026, 0, start, end);
    }

    private static AllocationSearchRow row(long allocationId, long eventId, LocalDate date, LocalTime start,
                                           int minutes) {
        return new AllocationSearchRow(allocationId, allocationId + 1000, date, eventId, EventType.RECURRING, null,
                start, minutes, "Materia " + eventId, "CUR-" + eventId, "Especialidad", 7L, 305, 3L);
    }

    @Test
    @DisplayName("q de un caracter tras el strip lanza InvalidSelectionException y no llama al repositorio")
    void rejectsShortQueryAfterStrip() {
        assertThatThrownBy(() -> service.search(" a ", DATE, 20))
                .isInstanceOf(InvalidSelectionException.class);

        verifyNoInteractions(academicPeriodService, buildingScopeResolver, repository);
    }

    @Test
    @DisplayName("q de exactamente 2 caracteres tras el strip es valido y se busca ya sin espacios de borde")
    void acceptsMinimumLengthQuery() {
        when(academicPeriodService.findActive())
                .thenReturn(List.of(period(LocalDate.of(2026, 3, 1), LocalDate.of(2026, 11, 30))));
        when(buildingScopeResolver.scopeFor(Permission.ALLOCATION_READ)).thenReturn(BuildingScope.unrestricted());
        when(repository.search(any(), any(), any(), any(), anyInt(), any())).thenReturn(List.of());

        service.search("  ab ", DATE, 20);

        verify(repository).search(eq("%ab%"), eq(DATE), any(), any(), eq(20), any());
    }

    @Test
    @DisplayName("el patron escapa los comodines de LIKE del texto buscado")
    void escapesLikeWildcards() {
        when(academicPeriodService.findActive())
                .thenReturn(List.of(period(LocalDate.of(2026, 3, 1), LocalDate.of(2026, 11, 30))));
        when(buildingScopeResolver.scopeFor(Permission.ALLOCATION_READ)).thenReturn(BuildingScope.unrestricted());
        when(repository.search(any(), any(), any(), any(), anyInt(), any())).thenReturn(List.of());

        service.search("5_%", DATE, 20);

        verify(repository).search(eq("%5\\_\\%%"), any(), any(), any(), anyInt(), any());
    }

    @Test
    @DisplayName("con dos periodos que contienen la fecha, el rango va del menor inicio al mayor fin y se ignora el que no la contiene")
    void rangeSpansAllPeriodsContainingDate() {
        when(academicPeriodService.findActive()).thenReturn(List.of(
                period(LocalDate.of(2026, 3, 1), LocalDate.of(2026, 11, 30)),
                period(LocalDate.of(2026, 8, 1), LocalDate.of(2026, 11, 30)),
                period(LocalDate.of(2026, 3, 1), LocalDate.of(2026, 7, 31))));
        when(buildingScopeResolver.scopeFor(Permission.ALLOCATION_READ)).thenReturn(BuildingScope.unrestricted());
        when(repository.search(any(), any(), any(), any(), anyInt(), any())).thenReturn(List.of());

        service.search("fisica", DATE, 20);

        verify(repository).search("%fisica%", DATE, LocalDate.of(2026, 3, 1), LocalDate.of(2026, 11, 30), 20,
                BuildingScope.unrestricted());
    }

    @Test
    @DisplayName("la fecha igual al inicio o al fin del periodo lo contiene")
    void periodBoundariesAreInclusive() {
        LocalDate start = LocalDate.of(2026, 3, 1);
        LocalDate end = LocalDate.of(2026, 11, 30);
        when(academicPeriodService.findActive()).thenReturn(List.of(period(start, end)));
        when(buildingScopeResolver.scopeFor(Permission.ALLOCATION_READ)).thenReturn(BuildingScope.unrestricted());
        when(repository.search(any(), any(), any(), any(), anyInt(), any())).thenReturn(List.of());

        service.search("fisica", start, 20);
        service.search("fisica", end, 20);

        verify(repository).search(any(), eq(start), eq(start), eq(end), anyInt(), any());
        verify(repository).search(any(), eq(end), eq(start), eq(end), anyInt(), any());
    }

    @Test
    @DisplayName("la fecha un dia despues del fin del periodo no lo contiene y devuelve vacio sin llamar al repositorio")
    void dateOneDayAfterPeriodEndIsOutside() {
        when(academicPeriodService.findActive())
                .thenReturn(List.of(period(LocalDate.of(2026, 3, 1), LocalDate.of(2026, 11, 30))));

        assertThat(service.search("fisica", LocalDate.of(2026, 12, 1), 20)).isEmpty();

        verifyNoInteractions(repository, buildingScopeResolver);
    }

    @Test
    @DisplayName("la fecha un dia antes del inicio del periodo no lo contiene")
    void dateOneDayBeforePeriodStartIsOutside() {
        when(academicPeriodService.findActive())
                .thenReturn(List.of(period(LocalDate.of(2026, 3, 1), LocalDate.of(2026, 11, 30))));

        assertThat(service.search("fisica", LocalDate.of(2026, 2, 28), 20)).isEmpty();

        verifyNoInteractions(repository, buildingScopeResolver);
    }

    @Test
    @DisplayName("un periodo sin fechas se ignora")
    void ignoresPeriodsWithoutDates() {
        when(academicPeriodService.findActive()).thenReturn(List.of(period(null, null)));

        assertThat(service.search("fisica", DATE, 20)).isEmpty();

        verifyNoInteractions(repository, buildingScopeResolver);
    }

    @Test
    @DisplayName("sin ningun periodo activo devuelve vacio sin llamar al repositorio")
    void noActivePeriodsReturnsEmpty() {
        when(academicPeriodService.findActive()).thenReturn(List.of());

        assertThat(service.search("fisica", DATE, 20)).isEmpty();

        verifyNoInteractions(repository, buildingScopeResolver);
    }

    @Test
    @DisplayName("con el alcance denegado devuelve vacio sin llamar al repositorio")
    void deniedScopeReturnsEmpty() {
        when(academicPeriodService.findActive())
                .thenReturn(List.of(period(LocalDate.of(2026, 3, 1), LocalDate.of(2026, 11, 30))));
        when(buildingScopeResolver.scopeFor(Permission.ALLOCATION_READ)).thenReturn(BuildingScope.denied());

        assertThat(service.search("fisica", DATE, 20)).isEmpty();

        verifyNoInteractions(repository);
    }

    @Test
    @DisplayName("pasa el alcance por edificios al repositorio")
    void passesBuildingScopeToRepository() {
        BuildingScope scope = BuildingScope.of(Set.of(3L));
        when(academicPeriodService.findActive())
                .thenReturn(List.of(period(LocalDate.of(2026, 3, 1), LocalDate.of(2026, 11, 30))));
        when(buildingScopeResolver.scopeFor(Permission.ALLOCATION_READ)).thenReturn(scope);
        when(repository.search(any(), any(), any(), any(), anyInt(), any())).thenReturn(List.of());

        service.search("fisica", DATE, 20);

        verify(repository).search(any(), any(), any(), any(), anyInt(), eq(scope));
    }

    @Test
    @DisplayName("filas de dos eventos intercaladas: conserva el orden de primera aparicion, agrupa y calcula endTime")
    void groupsInterleavedRowsKeepingFirstAppearanceOrder() {
        when(academicPeriodService.findActive())
                .thenReturn(List.of(period(LocalDate.of(2026, 3, 1), LocalDate.of(2026, 11, 30))));
        when(buildingScopeResolver.scopeFor(Permission.ALLOCATION_READ)).thenReturn(BuildingScope.unrestricted());
        when(repository.search(any(), any(), any(), any(), anyInt(), any())).thenReturn(List.of(
                row(1, 20, LocalDate.of(2026, 9, 16), LocalTime.of(8, 0), 90),
                row(2, 10, LocalDate.of(2026, 9, 17), LocalTime.of(18, 30), 135),
                row(3, 20, LocalDate.of(2026, 9, 23), LocalTime.of(8, 0), 90),
                row(4, 10, LocalDate.of(2026, 9, 24), LocalTime.of(18, 30), 135)));

        List<AllocationSearchResultDto> result = service.search("fisica", DATE, 20);

        assertThat(result).extracting(AllocationSearchResultDto::eventId).containsExactly(20L, 10L);
        assertThat(result.get(0).occurrences()).extracting(AllocationSearchOccurrenceDto::allocationId)
                .containsExactly(1L, 3L);
        assertThat(result.get(1).occurrences()).extracting(AllocationSearchOccurrenceDto::allocationId)
                .containsExactly(2L, 4L);
        assertThat(result.get(0).occurrences()).extracting(AllocationSearchOccurrenceDto::endTime)
                .containsExactly(LocalTime.of(9, 30), LocalTime.of(9, 30));
        assertThat(result.get(1).occurrences()).extracting(AllocationSearchOccurrenceDto::endTime)
                .containsExactly(LocalTime.of(20, 45), LocalTime.of(20, 45));
    }

    @Test
    @DisplayName("copia los datos del evento y de la ocurrencia a los DTOs, con classroomName igual al numero del aula")
    void mapsRowFieldsToDtos() {
        when(academicPeriodService.findActive())
                .thenReturn(List.of(period(LocalDate.of(2026, 3, 1), LocalDate.of(2026, 11, 30))));
        when(buildingScopeResolver.scopeFor(Permission.ALLOCATION_READ)).thenReturn(BuildingScope.unrestricted());
        when(repository.search(any(), any(), any(), any(), anyInt(), any())).thenReturn(List.of(
                row(1, 10, LocalDate.of(2026, 9, 16), LocalTime.of(8, 0), 90)));

        AllocationSearchResultDto result = service.search("fisica", DATE, 20).getFirst();

        assertThat(result.eventId()).isEqualTo(10L);
        assertThat(result.eventType()).isEqualTo(EventType.RECURRING);
        assertThat(result.uniqueEventType()).isNull();
        assertThat(result.subject()).isEqualTo("Materia 10");
        assertThat(result.section()).isEqualTo("CUR-10");
        assertThat(result.specialtyName()).isEqualTo("Especialidad");
        assertThat(result.occurrences()).singleElement().satisfies(o -> {
            assertThat(o.allocationId()).isEqualTo(1L);
            assertThat(o.occurrenceId()).isEqualTo(1001L);
            assertThat(o.date()).isEqualTo(LocalDate.of(2026, 9, 16));
            assertThat(o.startTime()).isEqualTo(LocalTime.of(8, 0));
            assertThat(o.classroomId()).isEqualTo(7L);
            assertThat(o.classroomName()).isEqualTo("305");
            assertThat(o.buildingId()).isEqualTo(3L);
        });
    }
}
