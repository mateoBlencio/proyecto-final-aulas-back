package ar.edu.utn.frc.siga.ingest.service.impl;

import ar.edu.utn.frc.siga.academic.dto.response.AcademicPeriodResponseDto;
import ar.edu.utn.frc.siga.academic.dto.response.CommissionResponseDto;
import ar.edu.utn.frc.siga.academic.dto.response.SpecialtyResponseDto;
import ar.edu.utn.frc.siga.academic.dto.response.StudyPlanResponseDto;
import ar.edu.utn.frc.siga.academic.dto.response.SubjectCommissionResponseDto;
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
import ar.edu.utn.frc.siga.ingest.dto.RowDto;
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

@Component
@RequiredArgsConstructor
class IngestRowResolver {

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

    private record AcademicRefs(SubjectResponseDto subject, CommissionResponseDto commission) {
    }

    private record SpaceRefs(BuildingResponseDto building, ClassroomResponseDto classroom) {
    }

    @Transactional
    ResolvedRow resolve(RowDto dto, TermType termType, int year, LocalDate startDate, LocalDate endDate,
            IngestCache cache, AtomicInteger periodsCreated) {
        AcademicRefs academic = resolveAcademicRefs(dto, termType, year, cache, periodsCreated);
        SpaceRefs space = resolveSpaceRefs(dto, cache);

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
                academic.subject().id(),
                academic.commission().id()
            )
        );

        return new ResolvedRow(eventResult.value(), eventResult.created(), academic.subject(), academic.commission(),
            space.building(), space.classroom());
    }

    private AcademicRefs resolveAcademicRefs(RowDto dto, TermType termType, int year, IngestCache cache,
            AtomicInteger periodsCreated) {
        cache.get(SpecialtyResponseDto.class, dto.specialtyCode(),
            () -> specialtyService.findBySpecialtyCode(dto.specialtyCode()));

        cache.get(StudyPlanResponseDto.class, dto.studyPlanCode() + "-" + dto.specialtyCode(), () ->
            studyPlanService.findByPlanCodeAndSpecialtyCode(dto.studyPlanCode(), dto.specialtyCode()));

        SubjectResponseDto subject = cache.get(SubjectResponseDto.class,
            dto.subjectCode() + "-" + dto.studyPlanCode() + "-" + dto.specialtyCode(), () ->
                subjectService.findByCodeAndStudyPlan(dto.subjectCode(), dto.studyPlanCode(), dto.specialtyCode()));

        AcademicPeriodResponseDto period = cache.get(AcademicPeriodResponseDto.class,
            year + "-" + termType.getSemester(), () -> {
                FindOrCreateResult<AcademicPeriodResponseDto> r = academicPeriodService.findOrCreate(year, termType);
                if (r.created()) periodsCreated.incrementAndGet();
                return r.value();
            });

        CommissionResponseDto commission = cache.get(CommissionResponseDto.class,
            dto.courseCode() + "-" + dto.commissionNumber() + "-" + period.year() + "-" + period.semester(), () ->
                commissionService.findByCourseAndNumberAndPeriod(
                    dto.courseCode(), dto.commissionNumber(), period.year(), period.semester()));

        cache.get(SubjectCommissionResponseDto.class, subject.id() + "-" + commission.id(), () ->
            subjectCommissionService.findBySubjectAndCommission(subject.id(), commission.id()));

        return new AcademicRefs(subject, commission);
    }

    private SpaceRefs resolveSpaceRefs(RowDto dto, IngestCache cache) {
        BuildingResponseDto building = cache.get(BuildingResponseDto.class, dto.buildingName(), () ->
            buildingService.findByName(dto.buildingName()));

        ClassroomResponseDto classroom = cache.get(ClassroomResponseDto.class,
            dto.roomNumber() + "-" + building.id(), () ->
                classroomService.findByRoomNumberAndBuilding(dto.roomNumber(), building.id()));

        return new SpaceRefs(building, classroom);
    }
}
