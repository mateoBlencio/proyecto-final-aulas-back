package ar.edu.utn.frc.siga.allocation.service.impl;

import ar.edu.utn.frc.siga.academic.service.AcademicPeriodService;
import ar.edu.utn.frc.siga.allocation.dto.response.AllocationConflictDto;
import ar.edu.utn.frc.siga.allocation.dto.response.UnallocatedConflictDto;
import ar.edu.utn.frc.siga.allocation.model.ConflictType;
import ar.edu.utn.frc.siga.allocation.repository.AllocationRepository;
import ar.edu.utn.frc.siga.common.exception.InvalidSortPropertyException;
import ar.edu.utn.frc.siga.common.security.BuildingScope;
import ar.edu.utn.frc.siga.common.security.BuildingScopeResolver;
import ar.edu.utn.frc.siga.events.dto.response.AcademicEventResponseDto;
import ar.edu.utn.frc.siga.events.dto.response.OccurrenceSlotDto;
import ar.edu.utn.frc.siga.events.model.OccurrenceStatus;
import ar.edu.utn.frc.siga.events.service.AcademicEventService;
import ar.edu.utn.frc.siga.events.service.OccurrenceService;
import ar.edu.utn.frc.siga.space.service.ClassroomService;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import static ar.edu.utn.frc.siga.allocation.service.impl.UnallocatedConflictTestFixtures.recurring;
import static ar.edu.utn.frc.siga.allocation.service.impl.UnallocatedConflictTestFixtures.unique;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("AllocationConflictServiceImpl: filtros, orden y paginado de UNALLOCATED")
class AllocationConflictServiceImplUnallocatedTest {

    private static final Set<ConflictType> UNALLOCATED = Set.of(ConflictType.UNALLOCATED);
    private static final LocalDate FROM = LocalDate.now();
    private static final LocalDate TO = LocalDate.now().plusDays(60);
    private static final LocalDate NEXT_MONDAY = FROM.plusDays(1).with(java.time.temporal.TemporalAdjusters.nextOrSame(DayOfWeek.MONDAY));

    @Mock
    private ClassroomService classroomService;
    @Mock
    private AcademicEventService academicEventService;
    @Mock
    private AcademicPeriodService academicPeriodService;
    @Mock
    private AllocationOccupancyReader occupancyReader;
    @Mock
    private OccurrenceService occurrenceService;
    @Mock
    private AllocationRepository allocationRepository;
    @Mock
    private BuildingScopeResolver buildingScopeResolver;

    @InjectMocks
    private AllocationConflictServiceImpl service;

    @BeforeEach
    void setUp() {
        lenient().when(allocationRepository.findByOccurrenceIdIn(any())).thenReturn(List.of());
        lenient().when(buildingScopeResolver.scopeFor(any())).thenReturn(BuildingScope.unrestricted());
    }

    private void seed(List<AcademicEventResponseDto> events) {
        List<OccurrenceSlotDto> slots = new ArrayList<>();
        for (AcademicEventResponseDto e : events) {
            slots.add(new OccurrenceSlotDto(e.id() * 10, e.id(), NEXT_MONDAY, e.startTime(), e.startTime().plusHours(1),
                    OccurrenceStatus.NEEDS_ROOM, e.enrolled(), 1, null, null, null, null));
        }
        Collections.reverse(slots);
        when(occurrenceService.findSlotsByStatusBetween(eq(OccurrenceStatus.NEEDS_ROOM), any(), any())).thenReturn(slots);
        when(academicEventService.findByIds(any())).thenReturn(List.copyOf(events));
    }

    private Page<AllocationConflictDto> find(String q, DayOfWeek day, String specialty, Pageable pageable) {
        return service.findConflicts(UNALLOCATED, FROM, TO, false, q, day, specialty, pageable);
    }

    private static List<Long> ids(Page<AllocationConflictDto> page) {
        return page.getContent().stream().map(c -> ((UnallocatedConflictDto) c).event().id()).toList();
    }

    private List<AcademicEventResponseDto> catalog() {
        return List.of(
                recurring(1, "Análisis Matemático", "Ingeniería en Sistemas", "1K1", DayOfWeek.MONDAY, LocalTime.of(8, 0), 30),
                recurring(2, "Física I", "Ingeniería Mecánica", "2K3", DayOfWeek.TUESDAY, LocalTime.of(10, 0), 50),
                unique(3, "Algebra", "Ingeniería en Sistemas", null, NEXT_MONDAY, LocalTime.of(9, 0), 20),
                recurring(4, "Sistemas de Gestión", "Ingeniería Industrial", "3K2", DayOfWeek.WEDNESDAY, LocalTime.of(14, 0), null));
    }

    @Test
    @DisplayName("sin filtros ni sort, ordena por id del evento")
    void sinFiltrosOrdenaPorId() {
        seed(catalog());

        assertThat(ids(find(null, null, null, PageRequest.of(0, 20)))).containsExactly(1L, 2L, 3L, 4L);
    }

    @Test
    @DisplayName("q busca en el nombre de la materia sin importar mayúsculas")
    void qPorMateria() {
        seed(catalog());

        assertThat(ids(find("FÍSICA", null, null, PageRequest.of(0, 20)))).containsExactly(2L);
    }

    @Test
    @DisplayName("q ignora tildes en ambos lados")
    void qIgnoraTildes() {
        seed(catalog());

        assertThat(ids(find("analisis", null, null, PageRequest.of(0, 20)))).containsExactly(1L);
        assertThat(ids(find("álgebra", null, null, PageRequest.of(0, 20)))).containsExactly(3L);
        assertThat(ids(find("ingenieria mecanica", null, null, PageRequest.of(0, 20)))).containsExactly(2L);
    }

    @Test
    @DisplayName("q busca en el código de comisión")
    void qPorComision() {
        seed(catalog());

        assertThat(ids(find("3k2", null, null, PageRequest.of(0, 20)))).containsExactly(4L);
    }

    @Test
    @DisplayName("q busca en el nombre de la especialidad")
    void qPorEspecialidad() {
        seed(catalog());

        assertThat(ids(find("sistemas", null, null, PageRequest.of(0, 20)))).containsExactly(1L, 3L, 4L);
    }

    @Test
    @DisplayName("q vacío o en blanco no filtra")
    void qEnBlancoNoFiltra() {
        seed(catalog());

        assertThat(ids(find("   ", null, null, PageRequest.of(0, 20)))).hasSize(4);
        assertThat(ids(find("", null, null, PageRequest.of(0, 20)))).hasSize(4);
    }

    @Test
    @DisplayName("q sin coincidencias devuelve página vacía")
    void qSinCoincidencias() {
        seed(catalog());

        Page<AllocationConflictDto> page = find("zzz", null, null, PageRequest.of(0, 20));

        assertThat(page.getContent()).isEmpty();
        assertThat(page.getTotalElements()).isZero();
    }

    @Test
    @DisplayName("dayOfWeek usa el día del evento recurrente y la fecha del evento único")
    void filtroDiaDeSemana() {
        seed(catalog());

        assertThat(ids(find(null, DayOfWeek.MONDAY, null, PageRequest.of(0, 20)))).containsExactly(1L, 3L);
        assertThat(ids(find(null, DayOfWeek.TUESDAY, null, PageRequest.of(0, 20)))).containsExactly(2L);
        assertThat(ids(find(null, DayOfWeek.SUNDAY, null, PageRequest.of(0, 20)))).isEmpty();
    }

    @Test
    @DisplayName("specialty es igualdad exacta: sin tildes ni mayúsculas distintas no coincide")
    void filtroEspecialidadExacta() {
        seed(catalog());

        assertThat(ids(find(null, null, "Ingeniería en Sistemas", PageRequest.of(0, 20)))).containsExactly(1L, 3L);
        assertThat(ids(find(null, null, "ingenieria en sistemas", PageRequest.of(0, 20)))).isEmpty();
        assertThat(ids(find(null, null, "Ingeniería", PageRequest.of(0, 20)))).isEmpty();
    }

    @Test
    @DisplayName("filtros combinados se aplican en AND")
    void filtrosCombinados() {
        seed(catalog());

        assertThat(ids(find("alg", DayOfWeek.MONDAY, "Ingeniería en Sistemas", PageRequest.of(0, 20)))).containsExactly(3L);
        assertThat(ids(find("fisica", DayOfWeek.MONDAY, null, PageRequest.of(0, 20)))).isEmpty();
        assertThat(ids(find(null, DayOfWeek.MONDAY, "Ingeniería Mecánica", PageRequest.of(0, 20)))).isEmpty();
    }

    @Test
    @DisplayName("el total de la página refleja los filtros, no el universo completo")
    void totalReflejaFiltros() {
        seed(catalog());

        Page<AllocationConflictDto> page = find(null, null, "Ingeniería en Sistemas", PageRequest.of(0, 1));

        assertThat(page.getTotalElements()).isEqualTo(2);
        assertThat(ids(page)).containsExactly(1L);
    }

    @Test
    @DisplayName("sort=subject,asc ordena por materia sin importar mayúsculas")
    void sortSubject() {
        seed(catalog());

        assertThat(ids(find(null, null, null, PageRequest.of(0, 20, Sort.by("subject"))))).containsExactly(3L, 1L, 2L, 4L);
        assertThat(ids(find(null, null, null, PageRequest.of(0, 20, Sort.by(Sort.Direction.DESC, "subject")))))
                .containsExactly(4L, 2L, 1L, 3L);
    }

    @Test
    @DisplayName("sort=startTime asc y desc")
    void sortStartTime() {
        seed(catalog());

        assertThat(ids(find(null, null, null, PageRequest.of(0, 20, Sort.by("startTime"))))).containsExactly(1L, 3L, 2L, 4L);
        assertThat(ids(find(null, null, null, PageRequest.of(0, 20, Sort.by(Sort.Direction.DESC, "startTime")))))
                .containsExactly(4L, 2L, 3L, 1L);
    }

    @Test
    @DisplayName("sort=enrolled trata null como 0")
    void sortEnrolled() {
        seed(catalog());

        assertThat(ids(find(null, null, null, PageRequest.of(0, 20, Sort.by("enrolled"))))).containsExactly(4L, 3L, 1L, 2L);
        assertThat(ids(find(null, null, null, PageRequest.of(0, 20, Sort.by(Sort.Direction.DESC, "enrolled")))))
                .containsExactly(2L, 1L, 3L, 4L);
    }

    @Test
    @DisplayName("sort=dayOfWeek: a igual día el recurrente va antes que el único")
    void sortDayOfWeek() {
        seed(catalog());

        assertThat(ids(find(null, null, null, PageRequest.of(0, 20, Sort.by("dayOfWeek"))))).containsExactly(1L, 3L, 2L, 4L);
        assertThat(ids(find(null, null, null, PageRequest.of(0, 20, Sort.by(Sort.Direction.DESC, "dayOfWeek")))))
                .containsExactly(4L, 2L, 3L, 1L);
    }

    @Test
    @DisplayName("sort=commissionCode deja los null al final tanto en asc como en desc")
    void sortCommissionCodeNullAlFinal() {
        seed(catalog());

        assertThat(ids(find(null, null, null, PageRequest.of(0, 20, Sort.by("commissionCode"))))).containsExactly(1L, 2L, 4L, 3L);
        assertThat(ids(find(null, null, null, PageRequest.of(0, 20, Sort.by(Sort.Direction.DESC, "commissionCode")))))
                .containsExactly(4L, 2L, 1L, 3L);
    }

    @Test
    @DisplayName("el sort se aplica sobre el resultado filtrado")
    void sortConFiltro() {
        seed(catalog());

        assertThat(ids(find(null, null, "Ingeniería en Sistemas", PageRequest.of(0, 20, Sort.by(Sort.Direction.DESC, "enrolled")))))
                .containsExactly(1L, 3L);
    }

    @Test
    @DisplayName("dos páginas consecutivas no repiten ni saltean filas aunque el criterio empate")
    void paginasConsecutivasSinRepetirNiSaltear() {
        List<AcademicEventResponseDto> events = new ArrayList<>();
        for (long id = 1; id <= 7; id++) {
            events.add(recurring(id, "Materia", "Esp", "1K1", DayOfWeek.MONDAY, LocalTime.of(8, 0), 30));
        }
        seed(events);
        Sort sort = Sort.by("startTime");

        List<Long> all = new ArrayList<>();
        for (int page = 0; page < 3; page++) {
            all.addAll(ids(find(null, null, null, PageRequest.of(page, 3, sort))));
        }

        assertThat(all).containsExactly(1L, 2L, 3L, 4L, 5L, 6L, 7L);
    }

    @Test
    @DisplayName("sort inválido con UNALLOCATED lanza InvalidSortPropertyException (400)")
    void sortInvalidoLanza() {
        assertThatThrownBy(() -> find(null, null, null, PageRequest.of(0, 20, Sort.by("enrolledd"))))
                .isInstanceOf(InvalidSortPropertyException.class)
                .hasMessageContaining("enrolledd");
    }

    @Test
    @DisplayName("sort inválido sin UNALLOCATED entre los tipos no se valida")
    void sortInvalidoSinUnallocatedSeIgnora() {
        Page<AllocationConflictDto> page = service.findConflicts(Set.of(ConflictType.OVERCROWDED), FROM, TO, false,
                null, null, null, PageRequest.of(0, 20, Sort.by("loQueSea")));

        assertThat(page.getContent()).isEmpty();
    }
}
