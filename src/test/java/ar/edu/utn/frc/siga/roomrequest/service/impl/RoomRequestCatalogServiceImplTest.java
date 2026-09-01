package ar.edu.utn.frc.siga.roomrequest.service.impl;

import ar.edu.utn.frc.siga.academic.service.SpecialtyService;
import ar.edu.utn.frc.siga.academic.service.SubjectCommissionService;
import ar.edu.utn.frc.siga.academic.service.SubjectService;
import ar.edu.utn.frc.siga.common.exception.ResourceNotFoundException;
import ar.edu.utn.frc.siga.roomrequest.dto.response.CommissionScheduleDto;
import ar.edu.utn.frc.siga.roomrequest.mapper.RoomRequestCatalogMapper;
import ar.edu.utn.frc.siga.roomrequest.validator.ClassScheduleService;
import ar.edu.utn.frc.siga.roomrequest.validator.ClassSlot;
import ar.edu.utn.frc.siga.space.service.ClassroomService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("RoomRequestCatalogServiceImpl.findCommissionSchedule")
class RoomRequestCatalogServiceImplTest {

    @Mock private SpecialtyService specialtyService;
    @Mock private SubjectService subjectService;
    @Mock private SubjectCommissionService subjectCommissionService;
    @Mock private ClassroomService classroomService;
    @Mock private ClassScheduleService classScheduleService;
    @Mock private RoomRequestCatalogMapper mapper;

    private RoomRequestCatalogServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new RoomRequestCatalogServiceImpl(specialtyService, subjectService, subjectCommissionService,
                classroomService, classScheduleService, mapper);
    }

    @Test
    @DisplayName("materia y comisión válidas: devuelve slots mapeados y fechas de cursado")
    void happy() {
        when(classScheduleService.slots(1L, 9L)).thenReturn(List.of(
                new ClassSlot(100L, DayOfWeek.TUESDAY, LocalTime.of(18, 0), LocalTime.of(20, 0))));
        when(classScheduleService.classDates(eq(1L), eq(9L), any()))
                .thenReturn(List.of(LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 8)));

        CommissionScheduleDto result = service.findCommissionSchedule(1L, 9L);

        assertThat(result.slots()).singleElement().satisfies(slot -> {
            assertThat(slot.recurringEventId()).isEqualTo(100L);
            assertThat(slot.dayOfWeek()).isEqualTo(DayOfWeek.TUESDAY);
            assertThat(slot.startTime()).isEqualTo(LocalTime.of(18, 0));
            assertThat(slot.endTime()).isEqualTo(LocalTime.of(20, 0));
        });
        assertThat(result.dates()).containsExactly(LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 8));
    }

    @Test
    @DisplayName("la comisión no pertenece a la materia: propaga el 404 y no consulta el cursado")
    void commissionNotOfSubject() {
        when(subjectCommissionService.findBySubjectAndCommission(1L, 9L))
                .thenThrow(ResourceNotFoundException.of("SubjectCommission", 9L));

        assertThatThrownBy(() -> service.findCommissionSchedule(1L, 9L))
                .isInstanceOf(ResourceNotFoundException.class);

        verifyNoInteractions(classScheduleService);
    }
}
