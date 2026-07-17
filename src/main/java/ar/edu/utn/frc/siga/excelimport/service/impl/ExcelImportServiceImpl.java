package ar.edu.utn.frc.siga.excelimport.service.impl;

import ar.edu.utn.frc.siga.academic.dto.response.AcademicPeriodResponseDto;
import ar.edu.utn.frc.siga.academic.dto.response.CommissionResponseDto;
import ar.edu.utn.frc.siga.academic.dto.response.SpecialtyResponseDto;
import ar.edu.utn.frc.siga.academic.dto.response.StudyPlanResponseDto;
import ar.edu.utn.frc.siga.academic.dto.response.SubjectResponseDto;
import ar.edu.utn.frc.siga.academic.model.TermType;
import ar.edu.utn.frc.siga.academic.service.AcademicPeriodService;
import ar.edu.utn.frc.siga.academic.service.CommissionService;
import ar.edu.utn.frc.siga.academic.service.SpecialtyService;
import ar.edu.utn.frc.siga.academic.service.StudyPlanService;
import ar.edu.utn.frc.siga.academic.service.SubjectCommissionService;
import ar.edu.utn.frc.siga.academic.service.SubjectService;
import ar.edu.utn.frc.siga.allocation.dto.request.AllocateFromDateRequestDto;
import ar.edu.utn.frc.siga.allocation.dto.request.CreateRecurringEventRequestDto;
import ar.edu.utn.frc.siga.allocation.service.AcademicEventService;
import ar.edu.utn.frc.siga.allocation.service.AllocationService;
import ar.edu.utn.frc.siga.common.dto.FindOrCreateResult;
import ar.edu.utn.frc.siga.excelimport.dto.ExcelRowDto;
import ar.edu.utn.frc.siga.excelimport.dto.ImportResultDto;
import ar.edu.utn.frc.siga.excelimport.exception.ExcelImportException;
import ar.edu.utn.frc.siga.excelimport.mapper.ExcelRowMapper;
import ar.edu.utn.frc.siga.excelimport.service.ExcelImportService;
import ar.edu.utn.frc.siga.excelimport.validator.ExcelTemplateValidator;
import ar.edu.utn.frc.siga.space.dto.response.BuildingResponseDto;
import ar.edu.utn.frc.siga.space.dto.response.ClassroomResponseDto;
import ar.edu.utn.frc.siga.space.service.BuildingService;
import ar.edu.utn.frc.siga.space.service.ClassroomService;
import jakarta.persistence.EntityManager;
import java.time.Duration;
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

/**
 * Implementación de la importación masiva desde Excel: valida la plantilla, recorre
 * las filas de datos y por cada una resuelve (o crea) especialidad, plan de estudios,
 * materia, período académico, comisión, edificio y aula, para finalmente crear el
 * evento recurrente y su asignación de aula. Usa {@link ImportCache} para no repetir
 * búsquedas/creaciones de la misma entidad entre filas de la misma importación.
 */
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
    private final EntityManager entityManager;

    /**
     * Procesa el archivo completo en una única transacción: recorre las filas de datos
     * desde la fila 7 hasta la primera fila vacía (fin de los datos), y por cada una
     * encadena la resolución/creación de entidades y la asignación de aula del evento.
     */
    @Override
    @Transactional
    public ImportResultDto importExcel(MultipartFile file) {
        String originalFilename = file.getOriginalFilename() != null ? file.getOriginalFilename() : "(sin nombre)";
        log.info("Iniciando importación Excel: {} - {} bytes", originalFilename, file.getSize());

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

            // Se resuelven por su efecto (crear/reusar y quedar cacheadas): los pasos
            // siguientes encadenan por clave natural (dto.specialtyCode()/studyPlanCode()),
            // no necesitan el DTO en sí.
            cache.getSpecialty(dto.specialtyCode(), () -> {
                FindOrCreateResult<SpecialtyResponseDto> r = specialtyService.findOrCreate(dto.specialtyCode());
                count(r, entitiesCreated, entitiesReused);
                return r.value();
            });

            cache.getStudyPlan(dto.studyPlanCode() + "-" + dto.specialtyCode(), () -> {
                FindOrCreateResult<StudyPlanResponseDto> r =
                    studyPlanService.findOrCreate(dto.studyPlanCode(), dto.specialtyCode());
                count(r, entitiesCreated, entitiesReused);
                return r.value();
            });

            SubjectResponseDto subject = cache.getSubject(
                dto.subjectCode() + "-" + dto.studyPlanCode() + "-" + dto.specialtyCode(), () -> {
                    FindOrCreateResult<SubjectResponseDto> r = subjectService.findOrCreate(
                        dto.subjectCode(), dto.subjectName(), dto.studyPlanCode(), dto.specialtyCode(), dto.termType());
                    count(r, entitiesCreated, entitiesReused);
                    return r.value();
                });

            AcademicPeriodResponseDto period = cache.getPeriod(year + "-" + termType.getSemester(), () -> {
                FindOrCreateResult<AcademicPeriodResponseDto> r = academicPeriodService.findOrCreate(year, termType);
                count(r, entitiesCreated, entitiesReused);
                return r.value();
            });

            int yearLevel = Character.getNumericValue(dto.courseCode().charAt(0));
            CommissionResponseDto commission = cache.getCommission(
                dto.courseCode() + "-" + dto.commissionNumber() + "-" + period.year() + "-" + period.semester(), () -> {
                    FindOrCreateResult<CommissionResponseDto> r = commissionService.findOrCreate(
                        dto.courseCode(), dto.commissionNumber(), yearLevel, period.year(), period.semester());
                    count(r, entitiesCreated, entitiesReused);
                    return r.value();
                });

            cache.getSubjectCommission(subject.id() + "-" + commission.id(), () -> {
                var r = subjectCommissionService.findOrCreate(subject.id(), commission.id(), dto.enrolledCount());
                count(r, entitiesCreated, entitiesReused);
                return r.value();
            });

            BuildingResponseDto building = cache.getBuilding(dto.buildingName(), () -> {
                FindOrCreateResult<BuildingResponseDto> r = buildingService.findOrCreate(dto.buildingName());
                count(r, entitiesCreated, entitiesReused);
                return r.value();
            });

            ClassroomResponseDto classroom = cache.getClassroom(dto.roomNumber() + "-" + building.id(), () -> {
                FindOrCreateResult<ClassroomResponseDto> r = classroomService.findOrCreate(
                    dto.roomNumber(), building.id(), dto.enrolledCount());
                count(r, entitiesCreated, entitiesReused);
                return r.value();
            });

            int durationMinutes = dto.durationMinutes() != null
                ? dto.durationMinutes()
                : (int) Duration.between(dto.startTime(), dto.endTime()).toMinutes();

            var eventResult = academicEventService.findOrCreateRecurringEvent(
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

            allocationService.importAssignmentsFromDate(
                new AllocateFromDateRequestDto(
                    eventResult.value(),
                    startDate,
                    classroom.id(),
                    "Importado de Excel"
                )
            );

            if (eventResult.created()) assignmentsCreated++;
            else assignmentsReused++;
            processedRows++;
            log.debug("Fila {}: subject={}, commission={}, classroom={}",
                rowNum, subject.name(), commission.commissionNumber(), dto.roomNumber());

            // Acota el persistence context de la única TX del import: sin esto, el dirty-check
            // de cada flush (auto o explícito) crece con todas las entidades ya manejadas.
            if (processedRows % 50 == 0) {
                entityManager.flush();
                entityManager.clear();
            }
        }

        log.info("Importación completada: {} filas, {} eventos creados", processedRows, assignmentsCreated);

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
