package ar.edu.utn.frc.classroom_allocation.excelimport.service.impl;

import ar.edu.utn.frc.classroom_allocation.career.model.Specialty;
import ar.edu.utn.frc.classroom_allocation.career.model.StudyPlan;
import ar.edu.utn.frc.classroom_allocation.career.model.Subject;
import ar.edu.utn.frc.classroom_allocation.career.repository.SpecialtyRepository;
import ar.edu.utn.frc.classroom_allocation.career.repository.StudyPlanRepository;
import ar.edu.utn.frc.classroom_allocation.career.repository.SubjectRepository;
import ar.edu.utn.frc.classroom_allocation.course.model.AcademicPeriod;
import ar.edu.utn.frc.classroom_allocation.course.model.Commission;
import ar.edu.utn.frc.classroom_allocation.course.model.SubjectCommission;
import ar.edu.utn.frc.classroom_allocation.course.repository.AcademicPeriodRepository;
import ar.edu.utn.frc.classroom_allocation.course.repository.CommissionRepository;
import ar.edu.utn.frc.classroom_allocation.course.repository.SubjectCommissionRepository;
import ar.edu.utn.frc.classroom_allocation.excelimport.dto.ExcelRowDto;
import ar.edu.utn.frc.classroom_allocation.excelimport.dto.ImportResultDto;
import ar.edu.utn.frc.classroom_allocation.excelimport.exception.ExcelImportException;
import ar.edu.utn.frc.classroom_allocation.excelimport.service.ExcelImportService;
import ar.edu.utn.frc.classroom_allocation.excelimport.mapper.ExcelRowMapper;
import ar.edu.utn.frc.classroom_allocation.excelimport.validator.ExcelTemplateValidator;
import ar.edu.utn.frc.classroom_allocation.schedule.model.ClassroomAssignment;
import ar.edu.utn.frc.classroom_allocation.schedule.model.TimeSlot;
import ar.edu.utn.frc.classroom_allocation.schedule.repository.ClassroomAssignmentRepository;
import ar.edu.utn.frc.classroom_allocation.schedule.repository.TimeSlotRepository;
import ar.edu.utn.frc.classroom_allocation.space.model.Building;
import ar.edu.utn.frc.classroom_allocation.space.model.Classroom;
import ar.edu.utn.frc.classroom_allocation.space.repository.BuildingRepository;
import ar.edu.utn.frc.classroom_allocation.space.repository.ClassroomRepository;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.Year;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
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
    private final SpecialtyRepository specialtyRepository;
    private final StudyPlanRepository studyPlanRepository;
    private final SubjectRepository subjectRepository;
    private final AcademicPeriodRepository academicPeriodRepository;
    private final CommissionRepository commissionRepository;
    private final SubjectCommissionRepository subjectCommissionRepository;
    private final TimeSlotRepository timeSlotRepository;
    private final ClassroomAssignmentRepository assignmentRepository;
    private final BuildingRepository buildingRepository;
    private final ClassroomRepository classroomRepository;

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

            Integer cuatrimestre = parseTermType(dto.termType(), rowNum);
            AcademicPeriod period = getOrCreateAcademicPeriod(year, cuatrimestre, cache,
                entitiesCreated, entitiesReused);

            int anioNivel = Character.getNumericValue(dto.courseCode().charAt(0));
            Commission commission = getOrCreateCommission(dto.courseCode(),
                dto.commissionNumber(), anioNivel, period, cache,
                entitiesCreated, entitiesReused);

            SubjectCommission subjectCommission = getOrCreateSubjectCommission(
                subject, commission, dto.enrolledCount(), cache,
                entitiesCreated, entitiesReused);

            LocalTime startTime = parseHHMM(dto.startTime());
            LocalTime endTime = parseHHMM(dto.endTime());
            TimeSlot timeSlot = getOrCreateTimeSlot(dto.dayOfWeek(),
                startTime, endTime, dto.durationMinutes(), cache,
                entitiesCreated, entitiesReused);

            Building building = buildingRepository
                .findByNameAndDeletedFalse(dto.buildingName())
                .orElseThrow(() -> new ExcelImportException(
                    "Building not found: '" + dto.buildingName() + "', row " + rowNum));

            Classroom classroom = classroomRepository
                .findByRoomNumberAndBuildingAndDeletedFalse(dto.roomNumber(), building)
                .orElseThrow(() -> new ExcelImportException(
                    "Classroom not found: '" + dto.roomNumber()
                        + "' in building '" + dto.buildingName() + "', row " + rowNum));

            ClassroomAssignment assignment = getOrCreateAssignment(
                subjectCommission, classroom, timeSlot);
            if (assignment == null) {
                log.info("Creando ClassroomAssignment: sc={}, aula={}, franja={}",
                    subjectCommission.getId(), classroom.getId(), timeSlot.getId());
                assignmentRepository.save(
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
                rowNum, subject.getNombre(), commission.getNumeroComision(), dto.roomNumber());
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
            return specialtyRepository.findByCodigoEspecialidadAndDeletedFalse(codigo)
                .map(found -> {
                    log.debug("Reusando Specialty: id={}", found.getId());
                    reused.incrementAndGet();
                    return found;
                })
                .orElseGet(() -> {
                    log.warn("Creando Specialty con nombre provisional: codigo={}", codigo);
                    created.incrementAndGet();
                    return specialtyRepository.save(
                        Specialty.builder()
                            .codigoEspecialidad(codigo)
                            .nombre(String.valueOf(codigo))
                            .build()
                    );
                });
        });
    }

    private StudyPlan getOrCreateStudyPlan(Integer codigoPlan, Specialty specialty,
                                            ImportCache cache,
                                            AtomicInteger created, AtomicInteger reused) {
        String key = codigoPlan + "-" + specialty.getId();
        return cache.getStudyPlan(key, () -> {
            log.debug("Cache miss for StudyPlan: {}", key);
            return studyPlanRepository
                .findByCodigoPlanAndEspecialidadAndDeletedFalse(codigoPlan, specialty)
                .map(found -> {
                    log.debug("Reusando StudyPlan: id={}", found.getId());
                    reused.incrementAndGet();
                    return found;
                })
                .orElseGet(() -> {
                    log.info("Creando StudyPlan: codigo={}, especialidad={}",
                        codigoPlan, specialty.getId());
                    created.incrementAndGet();
                    return studyPlanRepository.save(
                        StudyPlan.builder()
                            .codigoPlan(codigoPlan)
                            .especialidad(specialty)
                            .build()
                    );
                });
        });
    }

    private Subject getOrCreateSubject(Integer codigo, String nombre,
                                        StudyPlan plan, String dictado,
                                        ImportCache cache,
                                        AtomicInteger created, AtomicInteger reused) {
        String key = codigo + "-" + plan.getId();
        return cache.getSubject(key, () -> {
            log.debug("Cache miss for Subject: {}", key);
            return subjectRepository.findByCodigoMateriaAndPlanAndDeletedFalse(codigo, plan)
                .map(found -> {
                    log.debug("Reusando Subject: id={}", found.getId());
                    reused.incrementAndGet();
                    return found;
                })
                .orElseGet(() -> {
                    log.info("Creando Subject: codigo={}, plan={}", codigo, plan.getId());
                    created.incrementAndGet();
                    return subjectRepository.save(
                        Subject.builder()
                            .codigoMateria(codigo)
                            .nombre(nombre)
                            .plan(plan)
                            .dictado(dictado)
                            .build()
                    );
                });
        });
    }

    private AcademicPeriod getOrCreateAcademicPeriod(Integer anio, Integer cuatrimestre,
                                                      ImportCache cache,
                                                      AtomicInteger created,
                                                      AtomicInteger reused) {
        String key = anio + "-" + cuatrimestre;
        return cache.getPeriod(key, () -> {
            log.debug("Cache miss for AcademicPeriod: {}", key);
            return academicPeriodRepository.findByAnioAndCuatrimestre(anio, cuatrimestre)
                .map(found -> {
                    log.debug("Reusando AcademicPeriod: id={}", found.getId());
                    reused.incrementAndGet();
                    return found;
                })
                .orElseGet(() -> {
                    log.info("Creando AcademicPeriod: anio={}, cuatrimestre={}",
                        anio, cuatrimestre);
                    created.incrementAndGet();
                    return academicPeriodRepository.save(
                        AcademicPeriod.builder()
                            .anio(anio)
                            .cuatrimestre(cuatrimestre)
                            .build()
                    );
                });
        });
    }

    private Commission getOrCreateCommission(String courseCode, Integer commissionNumber,
                                              Integer anioNivel, AcademicPeriod period,
                                              ImportCache cache,
                                              AtomicInteger created, AtomicInteger reused) {
        String key = courseCode + "-" + commissionNumber + "-" + period.getId();
        return cache.getCommission(key, () -> {
            log.debug("Cache miss for Commission: {}", key);
            return commissionRepository
                .findByCodigoCursoAndNumeroComisionAndPeriodoAndDeletedFalse(
                    courseCode, commissionNumber, period)
                .map(found -> {
                    log.debug("Reusando Commission: id={}", found.getId());
                    reused.incrementAndGet();
                    return found;
                })
                .orElseGet(() -> {
                    log.info("Creando Commission: curso={}, comision={}, periodo={}",
                        courseCode, commissionNumber, period.getId());
                    created.incrementAndGet();
                    return commissionRepository.save(
                        Commission.builder()
                            .codigoCurso(courseCode)
                            .numeroComision(commissionNumber)
                            .anioNivel(anioNivel)
                            .periodo(period)
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
            return subjectCommissionRepository
                .findByMateriaAndComisionAndDeletedFalse(subject, commission)
                .map(found -> {
                    log.debug("Reusando SubjectCommission: id={}", found.getId());
                    reused.incrementAndGet();
                    return found;
                })
                .orElseGet(() -> {
                    log.info("Creando SubjectCommission: materia={}, comision={}",
                        subject.getId(), commission.getId());
                    created.incrementAndGet();
                    return subjectCommissionRepository.save(
                        SubjectCommission.builder()
                            .materia(subject)
                            .comision(commission)
                            .cantidadInscriptos(enrolledCount)
                            .build()
                    );
                });
        });
    }

    private TimeSlot getOrCreateTimeSlot(String diaSemana, LocalTime horaInicio,
                                          LocalTime horaFin, Integer duracionMinutos,
                                          ImportCache cache,
                                          AtomicInteger created, AtomicInteger reused) {
        String key = diaSemana + "-" + horaInicio + "-" + horaFin;
        return cache.getTimeSlot(key, () -> {
            log.debug("Cache miss for TimeSlot: {}", key);
            return timeSlotRepository
                .findByDiaSemanaAndHoraInicioAndHoraFin(diaSemana, horaInicio, horaFin)
                .map(found -> {
                    log.debug("Reusando TimeSlot: id={}", found.getId());
                    reused.incrementAndGet();
                    return found;
                })
                .orElseGet(() -> {
                    log.info("Creando TimeSlot: dia={}, inicio={}, fin={}",
                        diaSemana, horaInicio, horaFin);
                    created.incrementAndGet();
                    return timeSlotRepository.save(
                        TimeSlot.builder()
                            .diaSemana(diaSemana)
                            .horaInicio(horaInicio)
                            .horaFin(horaFin)
                            .duracionMinutos(duracionMinutos)
                            .build()
                    );
                });
        });
    }

    private ClassroomAssignment getOrCreateAssignment(SubjectCommission sc,
                                                       Classroom classroom,
                                                       TimeSlot slot) {
        return assignmentRepository
            .findByMateriaComisionAndAulaAndFranja(sc, classroom, slot)
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
