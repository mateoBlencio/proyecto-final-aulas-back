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
import java.util.ArrayList;
import java.util.List;
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
 * las filas de datos y por cada una busca especialidad, plan de estudios, materia,
 * comisión, materia-comisión, evento recurrente, edificio y aula (catálogo cargado por
 * fuera de esta app: falla si no existen), resuelve (o crea) el período académico y
 * finalmente asigna el aula al evento. Usa {@link ImportCache} para no repetir
 * búsquedas de la misma entidad entre filas de la misma importación.
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
        AtomicInteger periodsCreated = new AtomicInteger(0);

        // Se acumulan las requests de asignación de todas las filas y se aplican en un solo
        // batch al final: el cuello del import (~2 min con 1300 filas) era repetir, fila por
        // fila, las mismas 4 queries (evento, aula, occurrences, asignaciones existentes) que
        // una sola vez para todo el archivo. Ver AllocationService#importAllocationsBatch.
        List<AllocateFromDateRequestDto> pendingAllocations = new ArrayList<>();

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

            // Specialty/StudyPlan/Subject/Commission/SubjectCommission/RecurringEvent/Building/
            // Classroom son catálogo cargado por fuera de esta app: se buscan (fallan si no
            // existen), nunca se crean desde el import. Solo AcademicPeriod se crea acá.
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

            Long eventId = academicEventService.findRecurringEvent(
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

            pendingAllocations.add(new AllocateFromDateRequestDto(
                eventId,
                startDate,
                classroom.id(),
                "Importado de Excel"
            ));

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

        allocationService.importAllocationsBatch(pendingAllocations);

        log.info("Importación completada: {} filas, {} períodos creados", processedRows, periodsCreated.get());

        return new ImportResultDto(processedRows, periodsCreated.get());
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
