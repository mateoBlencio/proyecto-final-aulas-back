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
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Optional;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.argThat;
import org.mockito.Mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.multipart.MultipartFile;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ExcelImportServiceImplTest {

    @Mock private ExcelTemplateValidator validator;
    @Mock private ExcelRowMapper rowMapper;
    @Mock private SpecialtyRepository specialtyRepository;
    @Mock private StudyPlanRepository studyPlanRepository;
    @Mock private SubjectRepository subjectRepository;
    @Mock private AcademicPeriodRepository academicPeriodRepository;
    @Mock private CommissionRepository commissionRepository;
    @Mock private SubjectCommissionRepository subjectCommissionRepository;
    @Mock private TimeSlotRepository timeSlotRepository;
    @Mock private ClassroomAssignmentRepository assignmentRepository;
    @Mock private BuildingRepository buildingRepository;
    @Mock private ClassroomRepository classroomRepository;

    private ExcelImportServiceImpl service;
    private MultipartFile file;
    private org.apache.poi.xssf.usermodel.XSSFWorkbook workbook;
    private org.apache.poi.ss.usermodel.Sheet sheet;

    @BeforeEach
    void setUp() {
        service = new ExcelImportServiceImpl(validator, rowMapper,
            specialtyRepository, studyPlanRepository, subjectRepository,
            academicPeriodRepository, commissionRepository, subjectCommissionRepository,
            timeSlotRepository, assignmentRepository, buildingRepository, classroomRepository);

        file = org.mockito.Mockito.mock(MultipartFile.class);
        workbook = new org.apache.poi.xssf.usermodel.XSSFWorkbook();
        sheet = workbook.createSheet("Hoja1");
    }

    private void setupYearRow() {
        sheet.createRow(3).createCell(0).setCellValue("Año=2026");
    }

    private void setupHeaderRow() {
        String[] headers = {
            "Curso", "Comisión", "Aula", "Nombre Edificio", "Día", "Dictado",
            "Hora Comienzo", "Hora Fin", "Rango Horario", "Durac[min]",
            "Duracion[hs]", "Especialidad", "Plan", "Materia",
            "Nombre de materia", "Cantidad de Cursado"
        };
        org.apache.poi.ss.usermodel.Row headerRow = sheet.createRow(5);
        for (int i = 0; i < 16; i++) {
            headerRow.createCell(i).setCellValue(headers[i]);
        }
    }

    private org.apache.poi.ss.usermodel.Row addDataRow(int rowIndex, String curso, int comision,
                                                        int aula, String edificio, String dia,
                                                        String dictado, int horaInicio, int horaFin,
                                                        int especialidad, int plan, int materia,
                                                        String nombreMateria, int cantidad) {
        org.apache.poi.ss.usermodel.Row row = sheet.createRow(rowIndex);
        row.createCell(0).setCellValue(curso);
        row.createCell(1).setCellValue((double) comision);
        row.createCell(2).setCellValue((double) aula);
        row.createCell(3).setCellValue(edificio);
        row.createCell(4).setCellValue(dia);
        row.createCell(5).setCellValue(dictado);
        row.createCell(6).setCellValue((double) horaInicio);
        row.createCell(7).setCellValue((double) horaFin);
        row.createCell(8).setCellValue("Rango");
        row.createCell(9).setCellValue(90.0);
        row.createCell(10).setCellValue(1.5);
        row.createCell(11).setCellValue((double) especialidad);
        row.createCell(12).setCellValue((double) plan);
        row.createCell(13).setCellValue((double) materia);
        row.createCell(14).setCellValue(nombreMateria);
        row.createCell(15).setCellValue((double) cantidad);
        return row;
    }

    private ExcelRowDto createDto(String curso, int comision, String aula, String edificio,
                                   String dia, String dictado, int start, int end,
                                   int especialidad, int plan, int materia,
                                   String nombreMateria, int cantidad) {
        return new ExcelRowDto(curso, comision, aula, edificio, dia, dictado,
            start, end, 90, especialidad, plan, materia, nombreMateria, cantidad);
    }

    // ─── TEST: importExcel_shouldReturnCorrectCounters ─────────────────────

    @Test
    void importExcel_shouldReturnCorrectCounters() throws Exception {
        setupYearRow();
        setupHeaderRow();
        addDataRow(6, "1C1", 10, 513, "Edif. Dr. Gallardo", "Jueves",
            "1 Cuat.", 800, 1540, 31, 2023, 104, "Ing Civil I", 45);

        when(file.isEmpty()).thenReturn(false);
        when(file.getOriginalFilename()).thenReturn("test.xlsx");
        when(file.getInputStream()).thenReturn(new ByteArrayInputStream(workbookToBytes()));
        when(validator.validate(any())).thenReturn(workbook);

        ExcelRowDto dto = createDto("1C1", 10, "513", "Edif. Dr. Gallardo",
            "Jueves", "1 Cuat.", 800, 1540, 31, 2023, 104, "Ing Civil I", 45);

        when(rowMapper.map(any(), anyInt())).thenReturn(dto);

        when(specialtyRepository.findByCodigoEspecialidadAndDeletedFalse(31))
            .thenReturn(Optional.empty());
        when(specialtyRepository.save(any())).thenAnswer(invocation -> {
            Specialty s = invocation.getArgument(0);
            s.setId(1L);
            return s;
        });

        when(studyPlanRepository.findByCodigoPlanAndEspecialidadAndDeletedFalse(any(), any()))
            .thenReturn(Optional.empty());
        when(studyPlanRepository.save(any())).thenAnswer(invocation -> {
            StudyPlan sp = invocation.getArgument(0);
            sp.setId(1L);
            return sp;
        });

        when(subjectRepository.findByCodigoMateriaAndPlanAndDeletedFalse(any(), any()))
            .thenReturn(Optional.empty());
        when(subjectRepository.save(any())).thenAnswer(invocation -> {
            Subject s = invocation.getArgument(0);
            s.setId(1L);
            return s;
        });

        when(academicPeriodRepository.findByAnioAndCuatrimestre(any(), any()))
            .thenReturn(Optional.empty());
        when(academicPeriodRepository.save(any())).thenAnswer(invocation -> {
            AcademicPeriod ap = invocation.getArgument(0);
            ap.setId(1L);
            return ap;
        });

        when(commissionRepository.findByCodigoCursoAndNumeroComisionAndPeriodoAndDeletedFalse(
            any(), any(), any())).thenReturn(Optional.empty());
        when(commissionRepository.save(any())).thenAnswer(invocation -> {
            Commission c = invocation.getArgument(0);
            c.setId(1L);
            return c;
        });

        when(subjectCommissionRepository.findByMateriaAndComisionAndDeletedFalse(any(), any()))
            .thenReturn(Optional.empty());
        when(subjectCommissionRepository.save(any())).thenAnswer(invocation -> {
            SubjectCommission sc = invocation.getArgument(0);
            sc.setId(1L);
            return sc;
        });

        when(timeSlotRepository.findByDiaSemanaAndHoraInicioAndHoraFin(any(), any(), any()))
            .thenReturn(Optional.empty());
        when(timeSlotRepository.save(any())).thenAnswer(invocation -> {
            TimeSlot ts = invocation.getArgument(0);
            ts.setId(1L);
            return ts;
        });

        Building building = Building.builder().id(1).name("Edif. Dr. Gallardo").build();
        when(buildingRepository.findByNameAndDeletedFalse("Edif. Dr. Gallardo"))
            .thenReturn(Optional.of(building));

        Classroom classroom = Classroom.builder().id(1).roomNumber("513").building(building).build();
        when(classroomRepository.findByRoomNumberAndBuildingAndDeletedFalse("513", building))
            .thenReturn(Optional.of(classroom));

        when(assignmentRepository.findByMateriaComisionAndAulaAndFranja(any(), any(), any()))
            .thenReturn(Optional.empty());

        ImportResultDto result = service.importExcel(file);

        assertAll(
            () -> assertEquals(1, result.processedRows()),
            () -> assertEquals(1, result.assignmentsCreated()),
            () -> assertEquals(0, result.assignmentsReused())
        );
    }

    // ─── TEST: importExcel_shouldCreateSpecialtyWithCodeAsName ────────────

    @Test
    void importExcel_shouldCreateSpecialtyWithCodeAsName() throws Exception {
        setupYearRow();
        setupHeaderRow();
        addDataRow(6, "1C1", 10, 513, "Edif. Dr. Gallardo", "Jueves",
            "1 Cuat.", 800, 1540, 99, 2023, 104, "Materia", 30);

        when(file.getInputStream()).thenReturn(new ByteArrayInputStream(workbookToBytes()));
        when(validator.validate(any())).thenReturn(workbook);
        when(rowMapper.map(any(), anyInt())).thenReturn(
            createDto("1C1", 10, "513", "Edif. Dr. Gallardo", "Jueves",
                "1 Cuat.", 800, 1540, 99, 2023, 104, "Materia", 30));

        when(specialtyRepository.findByCodigoEspecialidadAndDeletedFalse(99))
            .thenReturn(Optional.empty());
        when(specialtyRepository.save(any())).thenAnswer(invocation -> {
            Specialty s = invocation.getArgument(0);
            s.setId(1L);
            return s;
        });

        when(studyPlanRepository.findByCodigoPlanAndEspecialidadAndDeletedFalse(any(), any()))
            .thenReturn(Optional.of(StudyPlan.builder().id(1L).build()));
        when(subjectRepository.findByCodigoMateriaAndPlanAndDeletedFalse(any(), any()))
            .thenReturn(Optional.of(Subject.builder().id(1L).build()));
        when(academicPeriodRepository.findByAnioAndCuatrimestre(any(), any()))
            .thenReturn(Optional.of(AcademicPeriod.builder().id(1L).build()));
        when(commissionRepository.findByCodigoCursoAndNumeroComisionAndPeriodoAndDeletedFalse(
            any(), any(), any())).thenReturn(Optional.of(Commission.builder().id(1L).build()));
        when(subjectCommissionRepository.findByMateriaAndComisionAndDeletedFalse(any(), any()))
            .thenReturn(Optional.of(SubjectCommission.builder().id(1L).build()));
        when(timeSlotRepository.findByDiaSemanaAndHoraInicioAndHoraFin(any(), any(), any()))
            .thenReturn(Optional.of(TimeSlot.builder().id(1L).build()));
        when(buildingRepository.findByNameAndDeletedFalse(any()))
            .thenReturn(Optional.of(Building.builder().id(1).build()));
        when(classroomRepository.findByRoomNumberAndBuildingAndDeletedFalse(any(), any()))
            .thenReturn(Optional.of(Classroom.builder().id(1).build()));
        when(assignmentRepository.findByMateriaComisionAndAulaAndFranja(any(), any(), any()))
            .thenReturn(Optional.of(ClassroomAssignment.builder().id(1L).build()));

        ImportResultDto result = service.importExcel(file);

        verify(specialtyRepository).save(any());
        assertEquals(1, result.entitiesCreated());
    }

    // ─── TEST: importExcel_shouldThrowWhenBuildingNotFound ────────────────

    @Test
    void importExcel_shouldThrowWhenBuildingNotFound() throws Exception {
        setupYearRow();
        setupHeaderRow();
        addDataRow(6, "1C1", 10, 513, "Edif. Inexistente", "Jueves",
            "1 Cuat.", 800, 1540, 31, 2023, 104, "Materia", 30);

        when(file.getInputStream()).thenReturn(new ByteArrayInputStream(workbookToBytes()));
        when(validator.validate(any())).thenReturn(workbook);
        when(rowMapper.map(any(), anyInt())).thenReturn(
            createDto("1C1", 10, "513", "Edif. Inexistente", "Jueves",
                "1 Cuat.", 800, 1540, 31, 2023, 104, "Materia", 30));

        when(specialtyRepository.findByCodigoEspecialidadAndDeletedFalse(any()))
            .thenReturn(Optional.of(Specialty.builder().id(1L).build()));
        when(studyPlanRepository.findByCodigoPlanAndEspecialidadAndDeletedFalse(any(), any()))
            .thenReturn(Optional.of(StudyPlan.builder().id(1L).build()));
        when(subjectRepository.findByCodigoMateriaAndPlanAndDeletedFalse(any(), any()))
            .thenReturn(Optional.of(Subject.builder().id(1L).build()));
        when(academicPeriodRepository.findByAnioAndCuatrimestre(any(), any()))
            .thenReturn(Optional.of(AcademicPeriod.builder().id(1L).build()));
        when(commissionRepository.findByCodigoCursoAndNumeroComisionAndPeriodoAndDeletedFalse(
            any(), any(), any())).thenReturn(Optional.of(Commission.builder().id(1L).build()));
        when(subjectCommissionRepository.findByMateriaAndComisionAndDeletedFalse(any(), any()))
            .thenReturn(Optional.of(SubjectCommission.builder().id(1L).build()));
        when(timeSlotRepository.findByDiaSemanaAndHoraInicioAndHoraFin(any(), any(), any()))
            .thenReturn(Optional.of(TimeSlot.builder().id(1L).build()));
        when(buildingRepository.findByNameAndDeletedFalse("Edif. Inexistente"))
            .thenReturn(Optional.empty());

        assertThrows(ExcelImportException.class, () -> service.importExcel(file));
    }

    // ─── TEST: importExcel_shouldThrowWhenTermTypeIsUnrecognized ──────────

    @Test
    void importExcel_shouldThrowWhenTermTypeIsUnrecognized() throws Exception {
        setupYearRow();
        setupHeaderRow();
        addDataRow(6, "1C1", 10, 513, "Edif. Dr. Gallardo", "Jueves",
            "Verano", 800, 1540, 31, 2023, 104, "Materia", 30);

        when(file.getInputStream()).thenReturn(new ByteArrayInputStream(workbookToBytes()));
        when(validator.validate(any())).thenReturn(workbook);
        when(rowMapper.map(any(), anyInt())).thenReturn(
            createDto("1C1", 10, "513", "Edif. Dr. Gallardo", "Jueves",
                "Verano", 800, 1540, 31, 2023, 104, "Materia", 30));

        when(specialtyRepository.findByCodigoEspecialidadAndDeletedFalse(any()))
            .thenReturn(Optional.of(Specialty.builder().id(1L).build()));
        when(studyPlanRepository.findByCodigoPlanAndEspecialidadAndDeletedFalse(any(), any()))
            .thenReturn(Optional.of(StudyPlan.builder().id(1L).build()));
        when(subjectRepository.findByCodigoMateriaAndPlanAndDeletedFalse(any(), any()))
            .thenReturn(Optional.of(Subject.builder().id(1L).build()));

        assertThrows(ExcelImportException.class, () -> service.importExcel(file));
    }

    // ─── TEST: importExcel_shouldReuseAssignmentWhenAlreadyExists ─────────

    @Test
    void importExcel_shouldReuseAssignmentWhenAlreadyExists() throws Exception {
        setupYearRow();
        setupHeaderRow();
        addDataRow(6, "1C1", 10, 513, "Edif. Dr. Gallardo", "Jueves",
            "1 Cuat.", 800, 1540, 31, 2023, 104, "Materia", 30);

        when(file.getInputStream()).thenReturn(new ByteArrayInputStream(workbookToBytes()));
        when(validator.validate(any())).thenReturn(workbook);
        when(rowMapper.map(any(), anyInt())).thenReturn(
            createDto("1C1", 10, "513", "Edif. Dr. Gallardo", "Jueves",
                "1 Cuat.", 800, 1540, 31, 2023, 104, "Materia", 30));

        when(specialtyRepository.findByCodigoEspecialidadAndDeletedFalse(any()))
            .thenReturn(Optional.of(Specialty.builder().id(1L).build()));
        when(studyPlanRepository.findByCodigoPlanAndEspecialidadAndDeletedFalse(any(), any()))
            .thenReturn(Optional.of(StudyPlan.builder().id(1L).build()));
        when(subjectRepository.findByCodigoMateriaAndPlanAndDeletedFalse(any(), any()))
            .thenReturn(Optional.of(Subject.builder().id(1L).build()));
        when(academicPeriodRepository.findByAnioAndCuatrimestre(any(), any()))
            .thenReturn(Optional.of(AcademicPeriod.builder().id(1L).build()));
        when(commissionRepository.findByCodigoCursoAndNumeroComisionAndPeriodoAndDeletedFalse(
            any(), any(), any())).thenReturn(Optional.of(Commission.builder().id(1L).build()));
        when(subjectCommissionRepository.findByMateriaAndComisionAndDeletedFalse(any(), any()))
            .thenReturn(Optional.of(SubjectCommission.builder().id(1L).build()));
        when(timeSlotRepository.findByDiaSemanaAndHoraInicioAndHoraFin(any(), any(), any()))
            .thenReturn(Optional.of(TimeSlot.builder().id(1L).build()));
        when(buildingRepository.findByNameAndDeletedFalse(any()))
            .thenReturn(Optional.of(Building.builder().id(1).build()));
        when(classroomRepository.findByRoomNumberAndBuildingAndDeletedFalse(any(), any()))
            .thenReturn(Optional.of(Classroom.builder().id(1).build()));
        when(assignmentRepository.findByMateriaComisionAndAulaAndFranja(any(), any(), any()))
            .thenReturn(Optional.of(ClassroomAssignment.builder().id(1L).build()));

        ImportResultDto result = service.importExcel(file);

        assertEquals(1, result.assignmentsReused());
        assertEquals(0, result.assignmentsCreated());
    }

    // ─── TEST: importExcel_shouldCreateAssignmentWithExcelMetadata ─────────

    @Test
    void importExcel_shouldCreateAssignmentWithExcelMetadata() throws Exception {
        setupYearRow();
        setupHeaderRow();
        addDataRow(6, "1C1", 10, 513, "Edif. Dr. Gallardo", "Jueves",
            "1 Cuat.", 800, 1540, 31, 2023, 104, "Materia", 30);

        when(file.getInputStream()).thenReturn(new ByteArrayInputStream(workbookToBytes()));
        when(validator.validate(any())).thenReturn(workbook);
        when(rowMapper.map(any(), anyInt())).thenReturn(
            createDto("1C1", 10, "513", "Edif. Dr. Gallardo", "Jueves",
                "1 Cuat.", 800, 1540, 31, 2023, 104, "Materia", 30));

        when(specialtyRepository.findByCodigoEspecialidadAndDeletedFalse(any()))
            .thenReturn(Optional.of(Specialty.builder().id(1L).build()));
        when(studyPlanRepository.findByCodigoPlanAndEspecialidadAndDeletedFalse(any(), any()))
            .thenReturn(Optional.of(StudyPlan.builder().id(1L).build()));
        when(subjectRepository.findByCodigoMateriaAndPlanAndDeletedFalse(any(), any()))
            .thenReturn(Optional.of(Subject.builder().id(1L).build()));
        when(academicPeriodRepository.findByAnioAndCuatrimestre(any(), any()))
            .thenReturn(Optional.of(AcademicPeriod.builder().id(1L).build()));
        when(commissionRepository.findByCodigoCursoAndNumeroComisionAndPeriodoAndDeletedFalse(
            any(), any(), any())).thenReturn(Optional.of(Commission.builder().id(1L).build()));
        when(subjectCommissionRepository.findByMateriaAndComisionAndDeletedFalse(any(), any()))
            .thenReturn(Optional.of(SubjectCommission.builder().id(1L).build()));
        when(timeSlotRepository.findByDiaSemanaAndHoraInicioAndHoraFin(any(), any(), any()))
            .thenReturn(Optional.of(TimeSlot.builder().id(1L).build()));
        when(buildingRepository.findByNameAndDeletedFalse(any()))
            .thenReturn(Optional.of(Building.builder().id(1).build()));
        when(classroomRepository.findByRoomNumberAndBuildingAndDeletedFalse(any(), any()))
            .thenReturn(Optional.of(Classroom.builder().id(1).build()));
        when(assignmentRepository.findByMateriaComisionAndAulaAndFranja(any(), any(), any()))
            .thenReturn(Optional.empty());

        ImportResultDto result = service.importExcel(file);

        assertEquals(1, result.assignmentsCreated());
    }

    // ─── TEST: importExcel_shouldInferAnioNivelFromCodigoCurso ────────────

    @Test
    void importExcel_shouldInferAnioNivelFromCodigoCurso() throws Exception {
        setupYearRow();
        setupHeaderRow();
        addDataRow(6, "3C2", 10, 513, "Edif. Dr. Gallardo", "Jueves",
            "1 Cuat.", 800, 1540, 31, 2023, 104, "Materia", 30);

        when(file.getInputStream()).thenReturn(new ByteArrayInputStream(workbookToBytes()));
        when(validator.validate(any())).thenReturn(workbook);
        when(rowMapper.map(any(), anyInt())).thenReturn(
            createDto("3C2", 10, "513", "Edif. Dr. Gallardo", "Jueves",
                "1 Cuat.", 800, 1540, 31, 2023, 104, "Materia", 30));

        when(specialtyRepository.findByCodigoEspecialidadAndDeletedFalse(any()))
            .thenReturn(Optional.of(Specialty.builder().id(1L).build()));
        when(studyPlanRepository.findByCodigoPlanAndEspecialidadAndDeletedFalse(any(), any()))
            .thenReturn(Optional.of(StudyPlan.builder().id(1L).build()));
        when(subjectRepository.findByCodigoMateriaAndPlanAndDeletedFalse(any(), any()))
            .thenReturn(Optional.of(Subject.builder().id(1L).build()));
        when(academicPeriodRepository.findByAnioAndCuatrimestre(any(), any()))
            .thenReturn(Optional.of(AcademicPeriod.builder().id(1L).build()));
        when(commissionRepository.findByCodigoCursoAndNumeroComisionAndPeriodoAndDeletedFalse(
            any(), any(), any())).thenReturn(Optional.empty());
        when(commissionRepository.save(any())).thenAnswer(invocation -> {
            Commission c = invocation.getArgument(0);
            c.setId(1L);
            return c;
        });
        when(subjectCommissionRepository.findByMateriaAndComisionAndDeletedFalse(any(), any()))
            .thenReturn(Optional.of(SubjectCommission.builder().id(1L).build()));
        when(timeSlotRepository.findByDiaSemanaAndHoraInicioAndHoraFin(any(), any(), any()))
            .thenReturn(Optional.of(TimeSlot.builder().id(1L).build()));
        when(buildingRepository.findByNameAndDeletedFalse(any()))
            .thenReturn(Optional.of(Building.builder().id(1).build()));
        when(classroomRepository.findByRoomNumberAndBuildingAndDeletedFalse(any(), any()))
            .thenReturn(Optional.of(Classroom.builder().id(1).build()));
        when(assignmentRepository.findByMateriaComisionAndAulaAndFranja(any(), any(), any()))
            .thenReturn(Optional.of(ClassroomAssignment.builder().id(1L).build()));

        service.importExcel(file);

        verify(commissionRepository).save(argThat(
            c -> c.getAnioNivel() != null && c.getAnioNivel() == 3));
    }

    // ─── TEST: importExcel_shouldAcceptZeroCantidadInscriptos ─────────────

    @Test
    void importExcel_shouldAcceptZeroCantidadInscriptos() throws Exception {
        setupYearRow();
        setupHeaderRow();
        addDataRow(6, "1C1", 10, 513, "Edif. Dr. Gallardo", "Jueves",
            "1 Cuat.", 800, 1540, 31, 2023, 104, "Materia", 0);

        when(file.getInputStream()).thenReturn(new ByteArrayInputStream(workbookToBytes()));
        when(validator.validate(any())).thenReturn(workbook);
        when(rowMapper.map(any(), anyInt())).thenReturn(
            createDto("1C1", 10, "513", "Edif. Dr. Gallardo", "Jueves",
                "1 Cuat.", 800, 1540, 31, 2023, 104, "Materia", 0));

        when(specialtyRepository.findByCodigoEspecialidadAndDeletedFalse(any()))
            .thenReturn(Optional.of(Specialty.builder().id(1L).build()));
        when(studyPlanRepository.findByCodigoPlanAndEspecialidadAndDeletedFalse(any(), any()))
            .thenReturn(Optional.of(StudyPlan.builder().id(1L).build()));
        when(subjectRepository.findByCodigoMateriaAndPlanAndDeletedFalse(any(), any()))
            .thenReturn(Optional.of(Subject.builder().id(1L).build()));
        when(academicPeriodRepository.findByAnioAndCuatrimestre(any(), any()))
            .thenReturn(Optional.of(AcademicPeriod.builder().id(1L).build()));
        when(commissionRepository.findByCodigoCursoAndNumeroComisionAndPeriodoAndDeletedFalse(
            any(), any(), any())).thenReturn(Optional.of(Commission.builder().id(1L).build()));
        when(subjectCommissionRepository.findByMateriaAndComisionAndDeletedFalse(any(), any()))
            .thenReturn(Optional.empty());
        when(subjectCommissionRepository.save(any())).thenAnswer(invocation -> {
            SubjectCommission sc = invocation.getArgument(0);
            sc.setId(1L);
            return sc;
        });
        when(timeSlotRepository.findByDiaSemanaAndHoraInicioAndHoraFin(any(), any(), any()))
            .thenReturn(Optional.of(TimeSlot.builder().id(1L).build()));
        when(buildingRepository.findByNameAndDeletedFalse(any()))
            .thenReturn(Optional.of(Building.builder().id(1).build()));
        when(classroomRepository.findByRoomNumberAndBuildingAndDeletedFalse(any(), any()))
            .thenReturn(Optional.of(Classroom.builder().id(1).build()));
        when(assignmentRepository.findByMateriaComisionAndAulaAndFranja(any(), any(), any()))
            .thenReturn(Optional.of(ClassroomAssignment.builder().id(1L).build()));

        ImportResultDto result = service.importExcel(file);

        assertEquals(1, result.processedRows());
    }

    private byte[] workbookToBytes() {
        java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
        try { workbook.write(baos); workbook.close(); } catch (Exception e) { throw new RuntimeException(e); }
        return baos.toByteArray();
    }
}
