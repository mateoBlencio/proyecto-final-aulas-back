package ar.edu.utn.frc.classroom_allocation.excelimport.service.impl;

import ar.edu.utn.frc.classroom_allocation.allocation.dto.response.AcademicEventResponseDto;
import ar.edu.utn.frc.classroom_allocation.allocation.model.EventType;
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
import ar.edu.utn.frc.classroom_allocation.course.service.AcademicPeriodService;
import ar.edu.utn.frc.classroom_allocation.course.service.CommissionService;
import ar.edu.utn.frc.classroom_allocation.course.service.SubjectCommissionService;
import ar.edu.utn.frc.classroom_allocation.excelimport.dto.ExcelRowDto;
import ar.edu.utn.frc.classroom_allocation.excelimport.dto.ImportResultDto;
import ar.edu.utn.frc.classroom_allocation.excelimport.exception.ExcelImportException;
import ar.edu.utn.frc.classroom_allocation.excelimport.mapper.ExcelRowMapper;
import ar.edu.utn.frc.classroom_allocation.excelimport.validator.ExcelTemplateValidator;
import ar.edu.utn.frc.classroom_allocation.space.model.Building;
import ar.edu.utn.frc.classroom_allocation.space.model.Classroom;
import ar.edu.utn.frc.classroom_allocation.space.service.BuildingService;
import ar.edu.utn.frc.classroom_allocation.space.service.ClassroomService;
import java.io.ByteArrayInputStream;
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.List;
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
import static org.mockito.ArgumentMatchers.eq;
import org.mockito.Mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.multipart.MultipartFile;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ExcelImportServiceImplTest {

    @Mock private ExcelTemplateValidator validator;
    @Mock private ExcelRowMapper rowMapper;
    @Mock private SpecialtyService specialtyService;
    @Mock private StudyPlanService studyPlanService;
    @Mock private SubjectService subjectService;
    @Mock private AcademicPeriodService academicPeriodService;
    @Mock private CommissionService commissionService;
    @Mock private SubjectCommissionService subjectCommissionService;
    @Mock private AcademicEventService academicEventService;
    @Mock private AllocationService allocationService;
    @Mock private BuildingService buildingService;
    @Mock private ClassroomService classroomService;

    private ExcelImportServiceImpl service;
    private MultipartFile file;
    private org.apache.poi.xssf.usermodel.XSSFWorkbook workbook;
    private org.apache.poi.ss.usermodel.Sheet sheet;

    @BeforeEach
    void setUp() {
        service = new ExcelImportServiceImpl(validator, rowMapper,
            specialtyService, studyPlanService, subjectService,
            academicPeriodService, commissionService, subjectCommissionService,
            academicEventService, allocationService, buildingService, classroomService);

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
                                   DayOfWeek dia, String dictado,
                                   LocalTime start, LocalTime end,
                                   int especialidad, int plan, int materia,
                                   String nombreMateria, int cantidad) {
        return new ExcelRowDto(curso, comision, aula, edificio, dia, dictado,
            start, end, 90, especialidad, plan, materia, nombreMateria, cantidad);
    }

    private void setupAllCareerMocksAsExisting() {
        Specialty specialty = Specialty.builder().id(1L).build();
        StudyPlan studyPlan = StudyPlan.builder().id(1L).build();
        Subject subject = Subject.builder().id(1L).name("Ing Civil I").build();
        AcademicPeriod period = AcademicPeriod.builder().id(1L).build();
        Commission commission = Commission.builder().id(1L).commissionNumber(10).build();
        SubjectCommission subjectCommission = SubjectCommission.builder().id(1L).build();

        when(specialtyService.findOrCreate(any()))
            .thenReturn(new FindOrCreateResult<>(specialty, false));
        when(studyPlanService.findOrCreate(any(), any()))
            .thenReturn(new FindOrCreateResult<>(studyPlan, false));
        when(subjectService.findOrCreate(any(), any(), any(), any()))
            .thenReturn(new FindOrCreateResult<>(subject, false));
        when(academicPeriodService.findOrCreate(any(), any()))
            .thenReturn(new FindOrCreateResult<>(period, false));
        when(commissionService.findOrCreate(any(), any(), any(), any()))
            .thenReturn(new FindOrCreateResult<>(commission, false));
        when(subjectCommissionService.findOrCreate(any(), any(), any()))
            .thenReturn(new FindOrCreateResult<>(subjectCommission, false));
    }

    private void setupBuildingAndClassroom() {
        Building building = Building.builder().id(1).name("Edif. Dr. Gallardo").build();
        Classroom classroom = Classroom.builder().id(1).roomNumber("513").building(building).build();

        when(buildingService.findOrCreate("Edif. Dr. Gallardo"))
            .thenReturn(new FindOrCreateResult<>(building, false));
        when(classroomService.findOrCreate(eq("513"), eq(building), any()))
            .thenReturn(new FindOrCreateResult<>(classroom, false));
    }

    private void setupAllocationMocks() {
        when(academicEventService.createRecurringEvent(any()))
            .thenReturn(AcademicEventResponseDto.builder().id(1L).type(EventType.RECURRING).build());
        when(allocationService.assignFromDate(any())).thenReturn(List.of());
    }

    // ─── TEST: importExcel_shouldReturnCorrectCounters ─────────────────────

    @Test
    void importExcel_shouldReturnCorrectCounters() throws Exception {
        setupYearRow();
        setupHeaderRow();
        addDataRow(6, "1C1", 10, 513, "Edif. Dr. Gallardo", "Jueves",
            "1 Cuat.", 800, 1540, 31, 2023, 104, "Ing Civil I", 45);

        when(file.getInputStream()).thenReturn(new ByteArrayInputStream(workbookToBytes()));
        when(validator.validate(any())).thenReturn(workbook);
        when(validator.extractYear(any())).thenReturn(2026);
        when(rowMapper.map(any(), anyInt())).thenReturn(
            createDto("1C1", 10, "513", "Edif. Dr. Gallardo",
                DayOfWeek.THURSDAY, "1 Cuat.",
                LocalTime.of(8, 0), LocalTime.of(15, 40),
                31, 2023, 104, "Ing Civil I", 45));

        when(specialtyService.findOrCreate(31))
            .thenReturn(new FindOrCreateResult<>(Specialty.builder().id(1L).build(), true));
        when(studyPlanService.findOrCreate(any(), any()))
            .thenReturn(new FindOrCreateResult<>(StudyPlan.builder().id(1L).build(), true));
        when(subjectService.findOrCreate(any(), any(), any(), any()))
            .thenReturn(new FindOrCreateResult<>(Subject.builder().id(1L).name("Ing Civil I").build(), true));
        when(academicPeriodService.findOrCreate(any(), any()))
            .thenReturn(new FindOrCreateResult<>(AcademicPeriod.builder().id(1L).build(), true));
        when(commissionService.findOrCreate(any(), any(), any(), any()))
            .thenReturn(new FindOrCreateResult<>(Commission.builder().id(1L).commissionNumber(10).build(), true));
        when(subjectCommissionService.findOrCreate(any(), any(), any()))
            .thenReturn(new FindOrCreateResult<>(SubjectCommission.builder().id(1L).build(), true));
        setupBuildingAndClassroom();
        setupAllocationMocks();

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
        when(validator.extractYear(any())).thenReturn(2026);
        when(rowMapper.map(any(), anyInt())).thenReturn(
            createDto("1C1", 10, "513", "Edif. Dr. Gallardo",
                DayOfWeek.THURSDAY, "1 Cuat.",
                LocalTime.of(8, 0), LocalTime.of(15, 40),
                99, 2023, 104, "Materia", 30));

        when(specialtyService.findOrCreate(99))
            .thenReturn(new FindOrCreateResult<>(Specialty.builder().id(1L).build(), true));
        when(studyPlanService.findOrCreate(any(), any()))
            .thenReturn(new FindOrCreateResult<>(StudyPlan.builder().id(1L).build(), false));
        when(subjectService.findOrCreate(any(), any(), any(), any()))
            .thenReturn(new FindOrCreateResult<>(Subject.builder().id(1L).name("Materia").build(), false));
        when(academicPeriodService.findOrCreate(any(), any()))
            .thenReturn(new FindOrCreateResult<>(AcademicPeriod.builder().id(1L).build(), false));
        when(commissionService.findOrCreate(any(), any(), any(), any()))
            .thenReturn(new FindOrCreateResult<>(Commission.builder().id(1L).commissionNumber(10).build(), false));
        when(subjectCommissionService.findOrCreate(any(), any(), any()))
            .thenReturn(new FindOrCreateResult<>(SubjectCommission.builder().id(1L).build(), false));
        setupBuildingAndClassroom();
        setupAllocationMocks();

        ImportResultDto result = service.importExcel(file);

        verify(specialtyService).findOrCreate(99);
        assertEquals(1, result.entitiesCreated());
    }

    // ─── TEST: importExcel_shouldCreateBuildingWhenNotFound ───────────────

    @Test
    void importExcel_shouldCreateBuildingWhenNotFound() throws Exception {
        setupYearRow();
        setupHeaderRow();
        addDataRow(6, "1C1", 10, 513, "Edificio Nuevo", "Jueves",
            "1 Cuat.", 800, 1540, 31, 2023, 104, "Materia", 30);

        when(file.getInputStream()).thenReturn(new ByteArrayInputStream(workbookToBytes()));
        when(validator.validate(any())).thenReturn(workbook);
        when(validator.extractYear(any())).thenReturn(2026);
        when(rowMapper.map(any(), anyInt())).thenReturn(
            createDto("1C1", 10, "513", "Edificio Nuevo",
                DayOfWeek.THURSDAY, "1 Cuat.",
                LocalTime.of(8, 0), LocalTime.of(15, 40),
                31, 2023, 104, "Materia", 30));

        setupAllCareerMocksAsExisting();

        Building newBuilding = Building.builder().id(2).name("Edificio Nuevo").build();
        Classroom newClassroom = Classroom.builder().id(2).roomNumber("513").building(newBuilding).build();
        when(buildingService.findOrCreate("Edificio Nuevo"))
            .thenReturn(new FindOrCreateResult<>(newBuilding, true));
        when(classroomService.findOrCreate(eq("513"), eq(newBuilding), any()))
            .thenReturn(new FindOrCreateResult<>(newClassroom, false));
        setupAllocationMocks();

        ImportResultDto result = service.importExcel(file);

        verify(buildingService).findOrCreate("Edificio Nuevo");
        assertEquals(1, result.entitiesCreated());
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
        when(validator.extractYear(any())).thenReturn(2026);
        when(rowMapper.map(any(), anyInt())).thenReturn(
            createDto("1C1", 10, "513", "Edif. Dr. Gallardo",
                DayOfWeek.THURSDAY, "Verano",
                LocalTime.of(8, 0), LocalTime.of(15, 40),
                31, 2023, 104, "Materia", 30));

        assertThrows(ExcelImportException.class, () -> service.importExcel(file));
    }

    // ─── TEST: importExcel_shouldCallCreateRecurringEventWithCorrectSubjectAndSection ──

    @Test
    void importExcel_shouldCallCreateRecurringEventWithCorrectSubjectAndSection() throws Exception {
        setupYearRow();
        setupHeaderRow();
        addDataRow(6, "1C1", 10, 513, "Edif. Dr. Gallardo", "Jueves",
            "1 Cuat.", 800, 1540, 31, 2023, 104, "Materia", 30);

        when(file.getInputStream()).thenReturn(new ByteArrayInputStream(workbookToBytes()));
        when(validator.validate(any())).thenReturn(workbook);
        when(validator.extractYear(any())).thenReturn(2026);
        when(rowMapper.map(any(), anyInt())).thenReturn(
            createDto("1C1", 10, "513", "Edif. Dr. Gallardo",
                DayOfWeek.THURSDAY, "1 Cuat.",
                LocalTime.of(8, 0), LocalTime.of(15, 40),
                31, 2023, 104, "Materia", 30));

        setupAllCareerMocksAsExisting();
        setupBuildingAndClassroom();
        setupAllocationMocks();

        service.importExcel(file);

        verify(academicEventService).createRecurringEvent(argThat(dto ->
            dto.subjectId().equals(1L) && dto.commissionId().equals(1L)
        ));
    }

    // ─── TEST: importExcel_shouldCallAssignFromDateAfterCreatingEvent ──────

    @Test
    void importExcel_shouldCallAssignFromDateAfterCreatingEvent() throws Exception {
        setupYearRow();
        setupHeaderRow();
        addDataRow(6, "1C1", 10, 513, "Edif. Dr. Gallardo", "Jueves",
            "1 Cuat.", 800, 1540, 31, 2023, 104, "Materia", 30);

        when(file.getInputStream()).thenReturn(new ByteArrayInputStream(workbookToBytes()));
        when(validator.validate(any())).thenReturn(workbook);
        when(validator.extractYear(any())).thenReturn(2026);
        when(rowMapper.map(any(), anyInt())).thenReturn(
            createDto("1C1", 10, "513", "Edif. Dr. Gallardo",
                DayOfWeek.THURSDAY, "1 Cuat.",
                LocalTime.of(8, 0), LocalTime.of(15, 40),
                31, 2023, 104, "Materia", 30));

        setupAllCareerMocksAsExisting();
        setupBuildingAndClassroom();
        when(academicEventService.createRecurringEvent(any()))
            .thenReturn(AcademicEventResponseDto.builder().id(42L).type(EventType.RECURRING).build());
        when(allocationService.assignFromDate(any())).thenReturn(List.of());

        ImportResultDto result = service.importExcel(file);

        verify(allocationService).assignFromDate(argThat(dto ->
            dto.recurringEventId().equals(42L)
                && "Importado de Excel".equals(dto.observation())
                && dto.classroomId().equals(1)
        ));
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
        when(validator.extractYear(any())).thenReturn(2026);
        when(rowMapper.map(any(), anyInt())).thenReturn(
            createDto("3C2", 10, "513", "Edif. Dr. Gallardo",
                DayOfWeek.THURSDAY, "1 Cuat.",
                LocalTime.of(8, 0), LocalTime.of(15, 40),
                31, 2023, 104, "Materia", 30));

        when(specialtyService.findOrCreate(any()))
            .thenReturn(new FindOrCreateResult<>(Specialty.builder().id(1L).build(), false));
        when(studyPlanService.findOrCreate(any(), any()))
            .thenReturn(new FindOrCreateResult<>(StudyPlan.builder().id(1L).build(), false));
        when(subjectService.findOrCreate(any(), any(), any(), any()))
            .thenReturn(new FindOrCreateResult<>(Subject.builder().id(1L).name("Materia").build(), false));
        when(academicPeriodService.findOrCreate(any(), any()))
            .thenReturn(new FindOrCreateResult<>(AcademicPeriod.builder().id(1L).build(), false));
        when(commissionService.findOrCreate(any(), any(), any(), any()))
            .thenReturn(new FindOrCreateResult<>(Commission.builder().id(1L).commissionNumber(10).build(), true));
        when(subjectCommissionService.findOrCreate(any(), any(), any()))
            .thenReturn(new FindOrCreateResult<>(SubjectCommission.builder().id(1L).build(), false));
        setupBuildingAndClassroom();
        setupAllocationMocks();

        service.importExcel(file);

        verify(commissionService).findOrCreate(any(), any(), eq(3), any());
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
        when(validator.extractYear(any())).thenReturn(2026);
        when(rowMapper.map(any(), anyInt())).thenReturn(
            createDto("1C1", 10, "513", "Edif. Dr. Gallardo",
                DayOfWeek.THURSDAY, "1 Cuat.",
                LocalTime.of(8, 0), LocalTime.of(15, 40),
                31, 2023, 104, "Materia", 0));

        setupAllCareerMocksAsExisting();
        setupBuildingAndClassroom();
        setupAllocationMocks();

        ImportResultDto result = service.importExcel(file);

        assertEquals(1, result.processedRows());
    }

    private byte[] workbookToBytes() {
        java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
        try { workbook.write(baos); workbook.close(); } catch (Exception e) { throw new RuntimeException(e); }
        return baos.toByteArray();
    }
}
