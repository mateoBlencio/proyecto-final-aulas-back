package ar.edu.utn.frc.siga.excelimport.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
import ar.edu.utn.frc.siga.allocation.dto.request.AllocateFromDateRequestDto;
import ar.edu.utn.frc.siga.allocation.dto.request.CreateRecurringEventRequestDto;
import ar.edu.utn.frc.siga.allocation.dto.response.AcademicEventResponseDto;
import ar.edu.utn.frc.siga.allocation.dto.response.RecurringEventResponseDto;
import ar.edu.utn.frc.siga.allocation.model.EventType;
import ar.edu.utn.frc.siga.allocation.service.AcademicEventService;
import ar.edu.utn.frc.siga.allocation.service.AllocationService;
import ar.edu.utn.frc.siga.common.dto.FindOrCreateResult;
import ar.edu.utn.frc.siga.excelimport.ExcelTestWorkbooks;
import ar.edu.utn.frc.siga.excelimport.ExcelTestWorkbooks.DataRow;
import ar.edu.utn.frc.siga.excelimport.dto.ImportResultDto;
import ar.edu.utn.frc.siga.excelimport.exception.ExcelImportException;
import ar.edu.utn.frc.siga.excelimport.mapper.ExcelRowMapper;
import ar.edu.utn.frc.siga.excelimport.validator.ExcelTemplateValidator;
import ar.edu.utn.frc.siga.space.dto.response.BuildingResponseDto;
import ar.edu.utn.frc.siga.space.dto.response.ClassroomResponseDto;
import ar.edu.utn.frc.siga.space.service.BuildingService;
import ar.edu.utn.frc.siga.space.service.ClassroomService;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;
import org.springframework.mock.web.MockMultipartFile;

@ExtendWith(MockitoExtension.class)
@DisplayName("ExcelImportServiceImpl")
class ExcelImportServiceImplTest {

    @Mock
    private SpecialtyService specialtyService;
    @Mock
    private StudyPlanService studyPlanService;
    @Mock
    private SubjectService subjectService;
    @Mock
    private AcademicPeriodService academicPeriodService;
    @Mock
    private CommissionService commissionService;
    @Mock
    private SubjectCommissionService subjectCommissionService;
    @Mock
    private AcademicEventService academicEventService;
    @Mock
    private AllocationService allocationService;
    @Mock
    private BuildingService buildingService;
    @Mock
    private ClassroomService classroomService;

    private ExcelImportServiceImpl service;

    @BeforeEach
    void setUp() {
        // validator y rowMapper reales: son la unidad de POI que se ejercita de punta a punta,
        // solo se mockean las fachadas de los otros módulos.
        service = new ExcelImportServiceImpl(new ExcelTemplateValidator(), new ExcelRowMapper(),
            specialtyService, studyPlanService, subjectService, academicPeriodService,
            commissionService, subjectCommissionService, academicEventService, allocationService,
            buildingService, classroomService);
    }

    @Test
    @DisplayName("fila válida: encadena los findOrCreate de todos los módulos con las claves correctas")
    void cadenaCompletaDeFindOrCreate() {
        stubHappyPath(DataRow.defaultRow());
        MockMultipartFile file = ExcelTestWorkbooks.validTemplate(2026).withValidDataRow().toMultipartFile();

        ImportResultDto result = service.importExcel(file);

        assertThat(result.processedRows()).isEqualTo(1);
        assertThat(result.assignmentsCreated()).isEqualTo(1);
        assertThat(result.assignmentsReused()).isZero();
        assertThat(result.entitiesCreated()).isEqualTo(8); // specialty, plan, subject, period, commission, subjectCommission, building, classroom
        assertThat(result.entitiesReused()).isZero();

        verify(specialtyService).findOrCreate(1);
        verify(studyPlanService).findOrCreate(1, 1);
        verify(subjectService).findOrCreate(100, "Análisis Matemático", 1, 1, "Anual");
        verify(academicPeriodService).findOrCreate(2026, TermType.ANUAL);
        verify(commissionService).findOrCreate("6301", 1, 6, 2026, 0);
        verify(subjectCommissionService).findOrCreate(10L, 20L, 30);
        verify(buildingService).findOrCreate("Edificio Central");
        verify(classroomService).findOrCreate("105", 5, 30);

        ArgumentCaptor<CreateRecurringEventRequestDto> eventCaptor =
            ArgumentCaptor.forClass(CreateRecurringEventRequestDto.class);
        verify(academicEventService).findOrCreateRecurringEvent(eventCaptor.capture());
        CreateRecurringEventRequestDto eventDto = eventCaptor.getValue();
        assertThat(eventDto.subjectId()).isEqualTo(10L);
        assertThat(eventDto.commissionId()).isEqualTo(20L);
        assertThat(eventDto.dayOfWeek()).isEqualTo(DayOfWeek.MONDAY);
        assertThat(eventDto.startDate()).isEqualTo(LocalDate.of(2026, 3, 1));
        assertThat(eventDto.endDate()).isEqualTo(LocalDate.of(2026, 11, 30)); // TermType.ANUAL

        ArgumentCaptor<AllocateFromDateRequestDto> allocationCaptor =
            ArgumentCaptor.forClass(AllocateFromDateRequestDto.class);
        verify(allocationService).importAssignmentsFromDate(allocationCaptor.capture());
        AllocateFromDateRequestDto allocationDto = allocationCaptor.getValue();
        assertThat(allocationDto.recurringEventId()).isEqualTo(1L);
        assertThat(allocationDto.classroomId()).isEqualTo(5);
        assertThat(allocationDto.observation()).isEqualTo("Importado de Excel");
    }

    @Test
    @DisplayName("ImportCache dedupea: dos filas de la misma especialidad → findOrCreate de specialty se llama una sola vez")
    void cacheDedupeaEspecialidadRepetida() {
        SpecialtyResponseDto specialty = new SpecialtyResponseDto(1, "Ingeniería en Sistemas");
        when(specialtyService.findOrCreate(1)).thenReturn(new FindOrCreateResult<>(specialty, true));
        stubRestOfChain(specialty, 100, "Análisis Matemático", 20L,
            "6301", 1, 30L, "105", 5, 40L, 1L);
        // Segunda fila: mismo curso/comisión (misma clave de comisión) pero distinta materia,
        // para no chocar con la clave de subject cacheada por otro código.
        stubForSecondSubject(specialty);

        MockMultipartFile file = ExcelTestWorkbooks.validTemplate(2026)
            .withValidDataRow()
            .withDataRow(DataRow.defaultRow().toBuilder().subjectCode(101).subjectName("Álgebra").build())
            .toMultipartFile();

        ImportResultDto result = service.importExcel(file);

        assertThat(result.processedRows()).isEqualTo(2);
        verify(specialtyService, times(1)).findOrCreate(1);
    }

    @Test
    @DisplayName("dictado (término) desconocido → ExcelImportException")
    void dictadoDesconocido() {
        MockMultipartFile file = ExcelTestWorkbooks.validTemplate(2026)
            .withDataRow(DataRow.defaultRow().toBuilder().termType("Trimestre Fantasma").build())
            .toMultipartFile();

        assertThatThrownBy(() -> service.importExcel(file))
            .isInstanceOf(ExcelImportException.class)
            .hasMessageContaining("Unknown term type")
            .hasMessageContaining("Trimestre Fantasma");

        verify(specialtyService, never()).findOrCreate(any());
    }

    @Test
    @DisplayName("Durac[min] vacía → duración se calcula como fin - inicio")
    void duracionConFallbackFinInicio() {
        // 18:30 a 20:00 = 90 minutos, sin Durac[min] explícita.
        stubHappyPath(DataRow.defaultRow().toBuilder().durationMinutes(null).build());
        MockMultipartFile file = ExcelTestWorkbooks.validTemplate(2026).withValidDataRow().toMultipartFile();

        service.importExcel(file);

        ArgumentCaptor<CreateRecurringEventRequestDto> eventCaptor =
            ArgumentCaptor.forClass(CreateRecurringEventRequestDto.class);
        verify(academicEventService).findOrCreateRecurringEvent(eventCaptor.capture());
        assertThat(eventCaptor.getValue().durationMinutes()).isEqualTo(90);
    }

    @Test
    @DisplayName("fila vacía corta el procesamiento: las filas siguientes no se procesan")
    void filaVaciaCortaElProcesamiento() {
        stubHappyPath(DataRow.defaultRow());
        MockMultipartFile file = ExcelTestWorkbooks.validTemplate(2026)
            .withValidDataRow()
            .withEmptyRow()
            .withDataRow(DataRow.defaultRow().toBuilder().subjectCode(999).build()) // nunca debería procesarse
            .toMultipartFile();

        ImportResultDto result = service.importExcel(file);

        assertThat(result.processedRows()).isEqualTo(1);
        verify(subjectService, times(1)).findOrCreate(any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("todas las entidades ya existían → contadores de reusadas, no de creadas")
    void contadoresDeEntidadesReusadas() {
        stubHappyPath(DataRow.defaultRow(), false);
        MockMultipartFile file = ExcelTestWorkbooks.validTemplate(2026).withValidDataRow().toMultipartFile();

        ImportResultDto result = service.importExcel(file);

        assertThat(result.entitiesCreated()).isZero();
        assertThat(result.entitiesReused()).isEqualTo(8);
        assertThat(result.assignmentsReused()).isEqualTo(1);
        assertThat(result.assignmentsCreated()).isZero();
    }

    // FIXME: bug de producción — ExcelImportServiceImpl.importExcel loguea `file.getName()`
    // (nombre del parámetro multipart, típicamente "file") en vez de
    // `file.getOriginalFilename()`. El log de arranque de la importación nunca muestra el
    // nombre real del archivo subido por el usuario. Ver docs/modulos/excelimport.md
    // ("Gaps y oportunidades"). Este test documenta el comportamiento actual (buggy), no lo
    // corrige.
    @Test
    @DisplayName("FIXME bug: el log de inicio muestra el nombre del parámetro multipart, no el nombre real del archivo")
    void logDeInicioNoMuestraElNombreRealDelArchivoFIXME() {
        stubHappyPath(DataRow.defaultRow());
        MockMultipartFile file = ExcelTestWorkbooks.validTemplate(2026).withValidDataRow()
            .toMultipartFile("planilla-2026.xlsx");

        Logger logger = (Logger) LoggerFactory.getLogger(ExcelImportServiceImpl.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            service.importExcel(file);
        } finally {
            logger.detachAppender(appender);
        }

        String startLog = appender.list.get(0).getFormattedMessage();
        assertThat(startLog).contains("file"); // MultipartFile#getName(): nombre del campo del form
        assertThat(startLog).doesNotContain("planilla-2026.xlsx"); // comportamiento actual: no aparece
    }

    // ---------- helpers de stubbing ----------

    private void stubHappyPath(DataRow row) {
        stubHappyPath(row, true);
    }

    private void stubHappyPath(DataRow row, boolean created) {
        SpecialtyResponseDto specialty = new SpecialtyResponseDto(row.specialtyCode(), "Ingeniería en Sistemas");
        when(specialtyService.findOrCreate(row.specialtyCode()))
            .thenReturn(new FindOrCreateResult<>(specialty, created));

        StudyPlanResponseDto plan = new StudyPlanResponseDto(row.studyPlanCode(), specialty);
        when(studyPlanService.findOrCreate(row.studyPlanCode(), row.specialtyCode()))
            .thenReturn(new FindOrCreateResult<>(plan, created));

        SubjectResponseDto subject = new SubjectResponseDto(10L, row.subjectCode(), row.subjectName(),
            row.termType(), plan);
        when(subjectService.findOrCreate(row.subjectCode(), row.subjectName(), row.studyPlanCode(),
            row.specialtyCode(), row.termType()))
            .thenReturn(new FindOrCreateResult<>(subject, created));

        AcademicPeriodResponseDto period = new AcademicPeriodResponseDto(2026, 0,
            LocalDate.of(2026, 3, 1), LocalDate.of(2026, 11, 30));
        when(academicPeriodService.findOrCreate(2026, TermType.ANUAL))
            .thenReturn(new FindOrCreateResult<>(period, created));

        int yearLevel = Character.getNumericValue(row.courseCode().charAt(0));
        CommissionResponseDto commission = new CommissionResponseDto(20L, row.courseCode(), row.commissionNumber(),
            yearLevel, period);
        when(commissionService.findOrCreate(row.courseCode(), row.commissionNumber(), yearLevel, 2026, 0))
            .thenReturn(new FindOrCreateResult<>(commission, created));

        SubjectCommissionResponseDto subjectCommission = new SubjectCommissionResponseDto(30L, 10L, 20L,
            row.enrolledCount());
        when(subjectCommissionService.findOrCreate(10L, 20L, row.enrolledCount()))
            .thenReturn(new FindOrCreateResult<>(subjectCommission, created));

        BuildingResponseDto building = new BuildingResponseDto(5, row.buildingName(), 5, true);
        when(buildingService.findOrCreate(row.buildingName()))
            .thenReturn(new FindOrCreateResult<>(building, created));

        ClassroomResponseDto classroom = new ClassroomResponseDto(5, String.valueOf(row.roomNumber()), 1, 40,
            true, 5, row.buildingName(), 1, "Aula");
        when(classroomService.findOrCreate(String.valueOf(row.roomNumber()), 5, row.enrolledCount()))
            .thenReturn(new FindOrCreateResult<>(classroom, created));

        RecurringEventResponseDto event = new RecurringEventResponseDto(1L, EventType.RECURRING,
            row.enrolledCount(), LocalTime.of(18, 30), 90L, DayOfWeek.MONDAY,
            LocalDate.of(2026, 3, 1), LocalDate.of(2026, 11, 30), subject, commission);
        when(academicEventService.findOrCreateRecurringEvent(any()))
            .thenReturn(new FindOrCreateResult<AcademicEventResponseDto>(event, created));
    }

    /** Variante de {@link #stubHappyPath} con ids explícitos, usada por el test de dedupe. */
    private void stubRestOfChain(SpecialtyResponseDto specialty, int subjectCode, String subjectName,
            long subjectId, String courseCode, int commissionNumber, long commissionId, String roomNumber,
            int buildingId, long subjectCommissionId, long eventId) {
        StudyPlanResponseDto plan = new StudyPlanResponseDto(1, specialty);
        when(studyPlanService.findOrCreate(1, 1)).thenReturn(new FindOrCreateResult<>(plan, true));

        SubjectResponseDto subject = new SubjectResponseDto(subjectId, subjectCode, subjectName, "Anual", plan);
        when(subjectService.findOrCreate(subjectCode, subjectName, 1, 1, "Anual"))
            .thenReturn(new FindOrCreateResult<>(subject, true));

        AcademicPeriodResponseDto period = new AcademicPeriodResponseDto(2026, 0,
            LocalDate.of(2026, 3, 1), LocalDate.of(2026, 11, 30));
        when(academicPeriodService.findOrCreate(2026, TermType.ANUAL)).thenReturn(new FindOrCreateResult<>(period, true));

        CommissionResponseDto commission = new CommissionResponseDto(commissionId, courseCode, commissionNumber, 6, period);
        when(commissionService.findOrCreate(courseCode, commissionNumber, 6, 2026, 0))
            .thenReturn(new FindOrCreateResult<>(commission, true));

        SubjectCommissionResponseDto subjectCommission =
            new SubjectCommissionResponseDto(subjectCommissionId, subjectId, commissionId, 30);
        when(subjectCommissionService.findOrCreate(subjectId, commissionId, 30))
            .thenReturn(new FindOrCreateResult<>(subjectCommission, true));

        BuildingResponseDto building = new BuildingResponseDto(buildingId, "Edificio Central", 5, true);
        when(buildingService.findOrCreate("Edificio Central")).thenReturn(new FindOrCreateResult<>(building, true));

        ClassroomResponseDto classroom = new ClassroomResponseDto(buildingId, roomNumber, 1, 40, true,
            buildingId, "Edificio Central", 1, "Aula");
        when(classroomService.findOrCreate(roomNumber, buildingId, 30)).thenReturn(new FindOrCreateResult<>(classroom, true));

        RecurringEventResponseDto event = new RecurringEventResponseDto(eventId, EventType.RECURRING, 30,
            LocalTime.of(18, 30), 90L, DayOfWeek.MONDAY, LocalDate.of(2026, 3, 1), LocalDate.of(2026, 11, 30),
            subject, commission);
        when(academicEventService.findOrCreateRecurringEvent(any()))
            .thenReturn(new FindOrCreateResult<AcademicEventResponseDto>(event, true));
    }

    private void stubForSecondSubject(SpecialtyResponseDto specialty) {
        StudyPlanResponseDto plan = new StudyPlanResponseDto(1, specialty);
        SubjectResponseDto subject = new SubjectResponseDto(21L, 101, "Álgebra", "Anual", plan);
        when(subjectService.findOrCreate(101, "Álgebra", 1, 1, "Anual"))
            .thenReturn(new FindOrCreateResult<>(subject, true));

        // Misma comisión (cacheada de la primera fila, id 30L) pero materia distinta (21L):
        // clave de subjectCommission distinta a la de la primera fila, requiere su propio stub.
        SubjectCommissionResponseDto subjectCommission = new SubjectCommissionResponseDto(41L, 21L, 30L, 30);
        when(subjectCommissionService.findOrCreate(21L, 30L, 30))
            .thenReturn(new FindOrCreateResult<>(subjectCommission, true));
    }
}
