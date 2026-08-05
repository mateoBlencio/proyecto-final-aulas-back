package ar.edu.utn.frc.siga.excelimport.service.impl;

import ar.edu.utn.frc.siga.academic.dto.response.AcademicPeriodResponseDto;
import ar.edu.utn.frc.siga.academic.dto.response.CommissionResponseDto;
import ar.edu.utn.frc.siga.academic.dto.response.SubjectResponseDto;
import ar.edu.utn.frc.siga.academic.model.TermType;
import ar.edu.utn.frc.siga.academic.service.AcademicPeriodService;
import ar.edu.utn.frc.siga.academic.service.CommissionService;
import ar.edu.utn.frc.siga.academic.service.SpecialtyService;
import ar.edu.utn.frc.siga.academic.service.StudyPlanService;
import ar.edu.utn.frc.siga.academic.service.SubjectCommissionService;
import ar.edu.utn.frc.siga.academic.service.SubjectService;
import ar.edu.utn.frc.siga.events.dto.request.CreateRecurringEventRequestDto;
import ar.edu.utn.frc.siga.events.service.AcademicEventService;
import ar.edu.utn.frc.siga.common.dto.FindOrCreateResult;
import ar.edu.utn.frc.siga.excelimport.dto.ExcelRowDto;
import ar.edu.utn.frc.siga.space.dto.response.BuildingResponseDto;
import ar.edu.utn.frc.siga.space.dto.response.ClassroomResponseDto;
import ar.edu.utn.frc.siga.space.service.BuildingService;
import ar.edu.utn.frc.siga.space.service.ClassroomService;
import java.time.Duration;
import java.time.LocalDate;
import java.util.concurrent.atomic.AtomicInteger;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Resuelve una fila de Excel contra el catálogo, en su propia transacción
 * ({@code REQUIRES_NEW} implícito: no hay transacción activa al invocarla desde
 * {@link ExcelImportServiceImpl}, que ya no envuelve el import completo). Necesario porque
 * cada fila puede fallar contra el catálogo (dato inconsistente en el origen) y el import
 * debe poder saltear esa fila sin perder el trabajo de las demás: si la resolución
 * corriera dentro de la transacción del import completo, una fila fallida marcaría toda
 * la transacción como rollback-only (comportamiento estándar de Spring ante una excepción
 * no capturada por un método transaccional), y el commit final abortaría igual aunque el
 * error se haya atrapado a nivel de Java.
 */
@Component
@RequiredArgsConstructor
class ExcelRowResolver {

    private final SpecialtyService specialtyService;
    private final StudyPlanService studyPlanService;
    private final SubjectService subjectService;
    private final AcademicPeriodService academicPeriodService;
    private final CommissionService commissionService;
    private final SubjectCommissionService subjectCommissionService;
    private final AcademicEventService academicEventService;
    private final BuildingService buildingService;
    private final ClassroomService classroomService;

    record ResolvedRow(Long eventId, boolean eventCreated, SubjectResponseDto subject,
            CommissionResponseDto commission, BuildingResponseDto building, ClassroomResponseDto classroom) {
    }

    @Transactional
    ResolvedRow resolve(ExcelRowDto dto, TermType termType, int year, LocalDate startDate, LocalDate endDate,
            ImportCache cache, AtomicInteger periodsCreated) {
        cache.getSpecialty(dto.specialtyCode(), () -> specialtyService.findBySpecialtyCode(dto.specialtyCode()));

        cache.getStudyPlan(dto.studyPlanCode() + "-" + dto.specialtyCode(), () ->
            studyPlanService.findByPlanCodeAndSpecialtyCode(dto.studyPlanCode(), dto.specialtyCode()));

        SubjectResponseDto subject = cache.getSubject(
            dto.subjectCode() + "-" + dto.studyPlanCode() + "-" + dto.specialtyCode(), () ->
                subjectService.findByCodeAndStudyPlan(dto.subjectCode(), dto.studyPlanCode(), dto.specialtyCode()));

        AcademicPeriodResponseDto period = cache.getPeriod(year + "-" + termType.getSemester(), () -> {
            FindOrCreateResult<AcademicPeriodResponseDto> r = academicPeriodService.findOrCreate(year, termType);
            if (r.created()) periodsCreated.incrementAndGet();
            return r.value();
        });

        CommissionResponseDto commission = cache.getCommission(
            dto.courseCode() + "-" + dto.commissionNumber() + "-" + period.year() + "-" + period.semester(), () ->
                commissionService.findByCourseAndNumberAndPeriod(
                    dto.courseCode(), dto.commissionNumber(), period.year(), period.semester()));

        cache.getSubjectCommission(subject.id() + "-" + commission.id(), () ->
            subjectCommissionService.findBySubjectAndCommission(subject.id(), commission.id()));

        BuildingResponseDto building = cache.getBuilding(dto.buildingName(), () ->
            buildingService.findByName(dto.buildingName()));

        ClassroomResponseDto classroom = cache.getClassroom(dto.roomNumber() + "-" + building.id(), () ->
            classroomService.findByRoomNumberAndBuilding(dto.roomNumber(), building.id()));

        int durationMinutes = dto.durationMinutes() != null
            ? dto.durationMinutes()
            : (int) Duration.between(dto.startTime(), dto.endTime()).toMinutes();

        FindOrCreateResult<Long> eventResult = academicEventService.findOrCreateRecurringEvent(
            new CreateRecurringEventRequestDto(
                dto.enrolledCount(),
                dto.startTime(),
                durationMinutes,
                dto.dayOfWeek(),
                startDate,
                endDate,
                subject.id(),
                commission.id()
            )
        );

        return new ResolvedRow(eventResult.value(), eventResult.created(), subject, commission, building, classroom);
    }
}
