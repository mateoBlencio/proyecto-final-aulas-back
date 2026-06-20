package ar.edu.utn.frc.classroom_allocation.excelimport.service.impl;

import ar.edu.utn.frc.classroom_allocation.career.model.Specialty;
import ar.edu.utn.frc.classroom_allocation.career.model.StudyPlan;
import ar.edu.utn.frc.classroom_allocation.career.model.Subject;
import ar.edu.utn.frc.classroom_allocation.career.service.SpecialtyService;
import ar.edu.utn.frc.classroom_allocation.career.service.StudyPlanService;
import ar.edu.utn.frc.classroom_allocation.career.service.SubjectService;
import ar.edu.utn.frc.classroom_allocation.common.exception.ResourceNotFoundException;
import ar.edu.utn.frc.classroom_allocation.course.model.AcademicPeriod;
import ar.edu.utn.frc.classroom_allocation.course.model.Commission;
import ar.edu.utn.frc.classroom_allocation.course.model.SubjectCommission;
import ar.edu.utn.frc.classroom_allocation.course.service.AcademicPeriodService;
import ar.edu.utn.frc.classroom_allocation.course.service.CommissionService;
import ar.edu.utn.frc.classroom_allocation.course.service.SubjectCommissionService;
import ar.edu.utn.frc.classroom_allocation.excelimport.dto.ExcelRowDto;
import ar.edu.utn.frc.classroom_allocation.excelimport.dto.ImportResultDto;
import ar.edu.utn.frc.classroom_allocation.excelimport.exception.ExcelImportException;
import ar.edu.utn.frc.classroom_allocation.excelimport.service.ExcelImportService;
import ar.edu.utn.frc.classroom_allocation.excelimport.mapper.ExcelRowMapper;
import ar.edu.utn.frc.classroom_allocation.excelimport.validator.ExcelTemplateValidator;
import ar.edu.utn.frc.classroom_allocation.schedule.model.ClassroomAssignment;
import ar.edu.utn.frc.classroom_allocation.schedule.model.TimeSlot;
import ar.edu.utn.frc.classroom_allocation.schedule.service.ClassroomAssignmentService;
import ar.edu.utn.frc.classroom_allocation.schedule.service.TimeSlotService;
import ar.edu.utn.frc.classroom_allocation.space.model.Building;
import ar.edu.utn.frc.classroom_allocation.space.model.Classroom;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.Year;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import ar.edu.utn.frc.classroom_allocation.space.service.BuildingService;
import ar.edu.utn.frc.classroom_allocation.space.service.ClassroomService;
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
    private final TimeSlotService timeSlotService;
    private final ClassroomAssignmentService assignmentService;
    private final ClassroomService classroomService;
    private final BuildingService buildingService;

    @Override
    @Transactional
    public ImportResultDto importExcel(MultipartFile file) {
        log.info("Iniciando importación Excel: {} - {} bytes", file.getName(), file.getSize());

        Workbook workbook = validator.validate(file);
        Sheet sheet = workbook.getSheet("Hoja1");

        int year = extractYear(sheet);
        ImportCache cache = new ImportCache();

        int processedRows = 0;
        int assignmentsCreated = 0;
        int assignmentsReused = 0;
        AtomicInteger entitiesCreated = new AtomicInteger(0);
        AtomicInteger entitiesReused = new AtomicInteger(0);

        for (int rowIndex = 6; rowIndex <= sheet.getLastRowNum(); rowIndex++) {
            Row row = sheet.getRow(rowIndex);
            if (isRowEmpty(row)) {
                break;
            }

            int rowNum = rowIndex + 1;
            ExcelRowDto dto = rowMapper.map(row, rowNum);

            Specialty specialty = getOrCreateSpecialty(dto.specialtyCode(), cache,
                entitiesCreated, entitiesReused);
            StudyPlan studyPlan = getOrCreateStudyPlan(dto.studyPlanCode(), specialty, cache,
                entitiesCreated, entitiesReused);
            Subject subject = getOrCreateSubject(dto.subjectCode(), dto.subjectName(),
                studyPlan, dto.termType(), cache, entitiesCreated, entitiesReused);

            Integer semester = parseTermType(dto.termType(), rowNum);
            AcademicPeriod period = getOrCreateAcademicPeriod(year, semester, cache,
                entitiesCreated, entitiesReused);

            int yearLevel = Character.getNumericValue(dto.courseCode().charAt(0));
            Commission commission = getOrCreateCommission(dto.courseCode(),
                dto.commissionNumber(), yearLevel, period, cache,
                entitiesCreated, entitiesReused);

            SubjectCommission subjectCommission = getOrCreateSubjectCommission(
                subject, commission, dto.enrolledCount(), cache,
                entitiesCreated, entitiesReused);

            LocalTime startTime = parseHHMM(dto.startTime());
            LocalTime endTime = parseHHMM(dto.endTime());
            TimeSlot timeSlot = getOrCreateTimeSlot(dto.dayOfWeek(),
                startTime, endTime, dto.durationMinutes(), cache,
                entitiesCreated, entitiesReused);

            Building building;
            try {
                building = buildingService.findByName(dto.buildingName());
            } catch (ResourceNotFoundException e) {
                throw new ExcelImportException("Row " + rowNum + ": " + e.getMessage(), e);
            }

            Classroom classroom;
            try {
                classroom = classroomService.findByRoomNumberAndBuilding(dto.roomNumber(), building);
            } catch (ResourceNotFoundException e) {
                throw new ExcelImportException("Row " + rowNum + ": " + e.getMessage(), e);
            }

            ClassroomAssignment assignment = getOrCreateAssignment(
                subjectCommission, classroom, timeSlot);
            if (assignment == null) {
                log.info("Creando ClassroomAssignment: sc={}, aula={}, timeSlot={}",
                    subjectCommission.getId(), classroom.getId(), timeSlot.getId());
                assignmentService.save(
                    ClassroomAssignment.builder()
                        .subjectCommission(subjectCommission)
                        .classroom(classroom)
                        .timeSlot(timeSlot)
                        .assignmentType("EXCEL")
                        .status("ACTIVA")
                        .createdAt(LocalDateTime.now())
                        .build()
                );
                assignmentsCreated++;
            } else {
                assignmentsReused++;
            }

            processedRows++;
            log.info("Fila {}: materia={}, comisión={}, aula={}",
                rowNum, subject.getName(), commission.getCommissionNumber(), dto.roomNumber());
        }

        log.info("Importación completada: {} filas, {} asignaciones creadas, {} reutilizadas",
            processedRows, assignmentsCreated, assignmentsReused);

        return new ImportResultDto(processedRows, assignmentsCreated,
            assignmentsReused, entitiesCreated.get(), entitiesReused.get());
    }

    private Specialty getOrCreateSpecialty(Integer codigo, ImportCache cache,
                                            AtomicInteger created, AtomicInteger reused) {
        return cache.getSpecialty(codigo, () -> {
            log.debug("Cache miss for Specialty: {}", codigo);
            return specialtyService.findBySpecialtyCodeAndDeletedFalse(codigo)
                .map(found -> {
                    log.debug("Reusando Specialty: id={}", found.getId());
                    reused.incrementAndGet();
                    return found;
                })
                .orElseGet(() -> {
                    log.warn("Creando Specialty con nombre provisional: codigo={}", codigo);
                    created.incrementAndGet();
                    return specialtyService.save(
                        Specialty.builder()
                            .specialtyCode(codigo)
                            .name(String.valueOf(codigo))
                            .build()
                    );
                });
        });
    }

    private StudyPlan getOrCreateStudyPlan(Integer planCode, Specialty specialty,
                                             ImportCache cache,
                                             AtomicInteger created, AtomicInteger reused) {
        String key = planCode + "-" + specialty.getId();
        return cache.getStudyPlan(key, () -> {
            log.debug("Cache miss for StudyPlan: {}", key);
            return studyPlanService
                .findByPlanCodeAndSpecialtyAndDeletedFalse(planCode, specialty)
                .map(found -> {
                    log.debug("Reusando StudyPlan: id={}", found.getId());
                    reused.incrementAndGet();
                    return found;
                })
                .orElseGet(() -> {
                    log.info("Creando StudyPlan: code={}, specialty={}",
                        planCode, specialty.getId());
                    created.incrementAndGet();
                    return studyPlanService.save(
                        StudyPlan.builder()
                            .planCode(planCode)
                            .specialty(specialty)
                            .build()
                    );
                });
        });
    }

    private Subject getOrCreateSubject(Integer code, String name,
                                        StudyPlan studyPlan, String term,
                                        ImportCache cache,
                                        AtomicInteger created, AtomicInteger reused) {
        String key = code + "-" + studyPlan.getId();
        return cache.getSubject(key, () -> {
            log.debug("Cache miss for Subject: {}", key);
            return subjectService.findByCodeAndStudyPlanAndDeletedFalse(code, studyPlan)
                .map(found -> {
                    log.debug("Reusando Subject: id={}", found.getId());
                    reused.incrementAndGet();
                    return found;
                })
                .orElseGet(() -> {
                    log.info("Creando Subject: code={}, plan={}", code, studyPlan.getId());
                    created.incrementAndGet();
                    return subjectService.save(
                        Subject.builder()
                            .code(code)
                            .name(name)
                            .studyPlan(studyPlan)
                            .term(term)
                            .build()
                    );
                });
        });
    }

    private AcademicPeriod getOrCreateAcademicPeriod(Integer year, Integer semester,
                                                       ImportCache cache,
                                                       AtomicInteger created,
                                                       AtomicInteger reused) {
        String key = year + "-" + semester;
        return cache.getPeriod(key, () -> {
            log.debug("Cache miss for AcademicPeriod: {}", key);
            return academicPeriodService.findByYearAndSemester(year, semester)
                .map(found -> {
                    log.debug("Reusando AcademicPeriod: id={}", found.getId());
                    reused.incrementAndGet();
                    return found;
                })
                .orElseGet(() -> {
                    log.info("Creando AcademicPeriod: year={}, semester={}",
                        year, semester);
                    created.incrementAndGet();
                    return academicPeriodService.save(
                        AcademicPeriod.builder()
                            .year(year)
                            .semester(semester)
                            .build()
                    );
                });
        });
    }

    private Commission getOrCreateCommission(String courseCode, Integer commissionNumber,
                                              Integer yearLevel, AcademicPeriod period,
                                              ImportCache cache,
                                              AtomicInteger created, AtomicInteger reused) {
        String key = courseCode + "-" + commissionNumber + "-" + period.getId();
        return cache.getCommission(key, () -> {
            log.debug("Cache miss for Commission: {}", key);
            return commissionService
                .findByCourseCodeAndCommissionNumberAndPeriodAndDeletedFalse(
                    courseCode, commissionNumber, period)
                .map(found -> {
                    log.debug("Reusando Commission: id={}", found.getId());
                    reused.incrementAndGet();
                    return found;
                })
                .orElseGet(() -> {
                    log.info("Creando Commission: course={}, commission={}, period={}",
                        courseCode, commissionNumber, period.getId());
                    created.incrementAndGet();
                    return commissionService.save(
                        Commission.builder()
                                .courseCode(courseCode)
                                .commissionNumber(commissionNumber)
                                .yearLevel(yearLevel)
                                .academicPeriod(period)
                                .build()
                    );
                });
        });
    }

    private SubjectCommission getOrCreateSubjectCommission(Subject subject, Commission commission,
                                                            Integer enrolledCount,
                                                            ImportCache cache,
                                                            AtomicInteger created,
                                                            AtomicInteger reused) {
        String key = subject.getId() + "-" + commission.getId();
        return cache.getSubjectCommission(key, () -> {
            log.debug("Cache miss for SubjectCommission: {}", key);
            return subjectCommissionService
                .findBySubjectAndCommissionAndDeletedFalse(subject, commission)
                .map(found -> {
                    log.debug("Reusando SubjectCommission: id={}", found.getId());
                    reused.incrementAndGet();
                    return found;
                })
                .orElseGet(() -> {
                    log.info("Creando SubjectCommission: subject={}, commission={}",
                        subject.getId(), commission.getId());
                    created.incrementAndGet();
                    return subjectCommissionService.save(
                        SubjectCommission.builder()
                            .subject(subject)
                            .commission(commission)
                            .enrolledCount(enrolledCount)
                            .build()
                    );
                });
        });
    }

    private TimeSlot getOrCreateTimeSlot(String dayOfWeek, LocalTime startTime,
                                          LocalTime endTime, Integer durationMinutes,
                                          ImportCache cache,
                                          AtomicInteger created, AtomicInteger reused) {
        String key = dayOfWeek + "-" + startTime + "-" + endTime;
        return cache.getTimeSlot(key, () -> {
            log.debug("Cache miss for TimeSlot: {}", key);
            return timeSlotService
                .findByDayOfWeekAndStartTimeAndEndTime(dayOfWeek, startTime, endTime)
                .map(found -> {
                    log.debug("Reusando TimeSlot: id={}", found.getId());
                    reused.incrementAndGet();
                    return found;
                })
                .orElseGet(() -> {
                    log.info("Creando TimeSlot: day={}, start={}, end={}",
                        dayOfWeek, startTime, endTime);
                    created.incrementAndGet();
                    return timeSlotService.save(
                        TimeSlot.builder()
                            .dayOfWeek(dayOfWeek)
                            .startTime(startTime)
                            .endTime(endTime)
                            .durationMinutes(durationMinutes)
                            .build()
                    );
                });
        });
    }

    private ClassroomAssignment getOrCreateAssignment(SubjectCommission sc,
                                                       Classroom classroom,
                                                       TimeSlot slot) {
        return assignmentService
            .findBySubjectCommissionAndClassroomAndTimeSlot(sc, classroom, slot)
            .orElse(null);
    }

    private Integer parseTermType(String termType, int rowNum) {
        return switch (termType) {
            case "Anual" -> 0;
            case "1 Cuat." -> 1;
            case "2 Cuat." -> 2;
            default -> throw new ExcelImportException(
                "Unknown term type: '" + termType + "', row " + rowNum);
        };
    }

    private LocalTime parseHHMM(int hhmm) {
        int hh = hhmm / 100;
        int mm = hhmm % 100;
        return LocalTime.of(hh, mm);
    }

    private int extractYear(Sheet sheet) {
        Row row = sheet.getRow(3);
        if (row == null) return Year.now().getValue();
        Cell cell = row.getCell(0);
        if (cell == null || cell.getCellType() != CellType.STRING) return Year.now().getValue();
        Matcher m = Pattern.compile("Año=(\\d{4})").matcher(cell.getStringCellValue());
        return m.find() ? Integer.parseInt(m.group(1)) : Year.now().getValue();
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
