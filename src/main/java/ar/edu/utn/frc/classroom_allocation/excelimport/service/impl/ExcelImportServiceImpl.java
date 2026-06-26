package ar.edu.utn.frc.classroom_allocation.excelimport.service.impl;

import ar.edu.utn.frc.classroom_allocation.allocation.dto.request.AllocateFromDateRequestDto;
import ar.edu.utn.frc.classroom_allocation.allocation.dto.request.CreateRecurringEventRequestDto;
import ar.edu.utn.frc.classroom_allocation.allocation.service.AcademicEventService;
import ar.edu.utn.frc.classroom_allocation.allocation.service.AllocationService;
import ar.edu.utn.frc.classroom_allocation.career.model.Specialty;
import ar.edu.utn.frc.classroom_allocation.career.model.StudyPlan;
import ar.edu.utn.frc.classroom_allocation.career.model.Subject;
import ar.edu.utn.frc.classroom_allocation.career.service.SpecialtyService;
import ar.edu.utn.frc.classroom_allocation.career.service.StudyPlanService;
import ar.edu.utn.frc.classroom_allocation.career.service.SubjectService;
import ar.edu.utn.frc.classroom_allocation.common.dto.FindOrCreateResult;
import ar.edu.utn.frc.classroom_allocation.course.model.AcademicPeriod;
import ar.edu.utn.frc.classroom_allocation.course.model.Commission;
import ar.edu.utn.frc.classroom_allocation.course.model.SubjectCommission;
import ar.edu.utn.frc.classroom_allocation.course.model.TermType;
import ar.edu.utn.frc.classroom_allocation.course.service.AcademicPeriodService;
import ar.edu.utn.frc.classroom_allocation.course.service.CommissionService;
import ar.edu.utn.frc.classroom_allocation.course.service.SubjectCommissionService;
import ar.edu.utn.frc.classroom_allocation.excelimport.dto.ExcelRowDto;
import ar.edu.utn.frc.classroom_allocation.excelimport.dto.ImportResultDto;
import ar.edu.utn.frc.classroom_allocation.excelimport.exception.ExcelImportException;
import ar.edu.utn.frc.classroom_allocation.excelimport.mapper.ExcelRowMapper;
import ar.edu.utn.frc.classroom_allocation.excelimport.service.ExcelImportService;
import ar.edu.utn.frc.classroom_allocation.excelimport.validator.ExcelTemplateValidator;
import ar.edu.utn.frc.classroom_allocation.space.model.Building;
import ar.edu.utn.frc.classroom_allocation.space.model.Classroom;
import ar.edu.utn.frc.classroom_allocation.space.service.BuildingService;
import ar.edu.utn.frc.classroom_allocation.space.service.ClassroomService;
import java.time.LocalDate;
import java.util.concurrent.atomic.AtomicInteger;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
@RequiredArgsConstructor
@Slf4j
public class ExcelImportServiceImpl implements ExcelImportService {

    private final ExcelTemplateValidator validator;
    private final ExcelRowMapper rowMapper;
    private final SpecialtyService specialtyService;
    private final StudyPlanService studyPlanService;
    private final SubjectService subjectService;
    private final AcademicPeriodService academicPeriodService;
    private final CommissionService commissionService;
    private final SubjectCommissionService subjectCommissionService;
    private final AcademicEventService academicEventService;
    private final AllocationService allocationService;
    private final BuildingService buildingService;
    private final ClassroomService classroomService;

    @Override
    @Transactional
    public ImportResultDto importExcel(MultipartFile file) {
        log.info("Starting Excel import: {} - {} bytes", file.getName(), file.getSize());

        Workbook workbook = validator.validate(file);
        Sheet sheet = workbook.getSheet("Hoja1");

        int year = validator.extractYear(sheet);
        ImportCache cache = new ImportCache();

        int processedRows = 0;
        int assignmentsCreated = 0;
        int assignmentsReused = 0;
        AtomicInteger entitiesCreated = new AtomicInteger(0);
        AtomicInteger entitiesReused = new AtomicInteger(0);

        for (int rowIndex = 6; rowIndex <= sheet.getLastRowNum(); rowIndex++) {
            Row row = sheet.getRow(rowIndex);
            if (isRowEmpty(row)) break;

            int rowNum = rowIndex + 1;
            ExcelRowDto dto = rowMapper.map(row, rowNum);

            TermType termType = TermType.fromLabel(dto.termType())
                .orElseThrow(() -> new ExcelImportException(
                    "Unknown term type: '" + dto.termType() + "', row " + rowNum));
            LocalDate startDate = termType.startDate(year);
            LocalDate endDate = termType.endDate(year);

            Specialty specialty = cache.getSpecialty(dto.specialtyCode(), () -> {
                FindOrCreateResult<Specialty> r = specialtyService.findOrCreate(dto.specialtyCode());
                count(r, entitiesCreated, entitiesReused);
                return r.entity();
            });

            StudyPlan studyPlan = cache.getStudyPlan(dto.studyPlanCode() + "-" + specialty.getId(), () -> {
                FindOrCreateResult<StudyPlan> r = studyPlanService.findOrCreate(dto.studyPlanCode(), specialty);
                count(r, entitiesCreated, entitiesReused);
                return r.entity();
            });

            Subject subject = cache.getSubject(dto.subjectCode() + "-" + studyPlan.getId(), () -> {
                FindOrCreateResult<Subject> r = subjectService.findOrCreate(
                    dto.subjectCode(), dto.subjectName(), studyPlan, dto.termType());
                count(r, entitiesCreated, entitiesReused);
                return r.entity();
            });

            AcademicPeriod period = cache.getPeriod(year + "-" + termType.getSemester(), () -> {
                FindOrCreateResult<AcademicPeriod> r = academicPeriodService.findOrCreate(year, termType);
                count(r, entitiesCreated, entitiesReused);
                return r.entity();
            });

            int yearLevel = Character.getNumericValue(dto.courseCode().charAt(0));
            Commission commission = cache.getCommission(
                dto.courseCode() + "-" + dto.commissionNumber() + "-" + period.getId(), () -> {
                    FindOrCreateResult<Commission> r = commissionService.findOrCreate(
                        dto.courseCode(), dto.commissionNumber(), yearLevel, period);
                    count(r, entitiesCreated, entitiesReused);
                    return r.entity();
                });

            cache.getSubjectCommission(subject.getId() + "-" + commission.getId(), () -> {
                FindOrCreateResult<SubjectCommission> r = subjectCommissionService.findOrCreate(
                    subject, commission, dto.enrolledCount());
                count(r, entitiesCreated, entitiesReused);
                return r.entity();
            });

            Building building = cache.getBuilding(dto.buildingName(), () -> {
                FindOrCreateResult<Building> r = buildingService.findOrCreate(dto.buildingName());
                count(r, entitiesCreated, entitiesReused);
                return r.entity();
            });

            Classroom classroom = cache.getClassroom(dto.roomNumber() + "-" + building.getId(), () -> {
                FindOrCreateResult<Classroom> r = classroomService.findOrCreate(
                    dto.roomNumber(), building, dto.enrolledCount());
                count(r, entitiesCreated, entitiesReused);
                return r.entity();
            });

            int durationMinutes = dto.durationMinutes() != null
                ? dto.durationMinutes()
                : (int) java.time.Duration.between(dto.startTime(), dto.endTime()).toMinutes();

            var event = academicEventService.createRecurringEvent(
                new CreateRecurringEventRequestDto(
                    dto.enrolledCount(),
                    dto.startTime(),
                    durationMinutes,
                    dto.dayOfWeek(),
                    startDate,
                    endDate,
                    subject.getId(),
                    commission.getId()
                )
            );

            allocationService.assignFromDate(
                new AllocateFromDateRequestDto(
                    event.getId(),
                    startDate,
                    classroom.getId(),
                    "Importado de Excel"
                )
            );

            assignmentsCreated++;
            processedRows++;
            log.info("Row {}: subject={}, commission={}, classroom={}",
                rowNum, subject.getName(), commission.getCommissionNumber(), dto.roomNumber());
        }

        log.info("Import completed: {} rows, {} events created", processedRows, assignmentsCreated);

        return new ImportResultDto(processedRows, assignmentsCreated,
            assignmentsReused, entitiesCreated.get(), entitiesReused.get());
    }

    private <T> void count(FindOrCreateResult<T> result, AtomicInteger created, AtomicInteger reused) {
        if (result.created()) created.incrementAndGet();
        else reused.incrementAndGet();
    }

    private boolean isRowEmpty(Row row) {
        if (row == null) return true;
        for (int i = 0; i < 16; i++) {
            Cell cell = row.getCell(i);
            if (cell != null && cell.getCellType() != CellType.BLANK) {
                return false;
            }
        }
        return true;
    }
}
