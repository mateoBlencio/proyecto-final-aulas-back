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
import ar.edu.utn.frc.siga.events.dto.request.CreateRecurringEventRequestDto;
import ar.edu.utn.frc.siga.events.dto.response.RecurringEventResponseDto;
import ar.edu.utn.frc.siga.events.model.EventType;
import ar.edu.utn.frc.siga.events.service.AcademicEventService;
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
import java.util.List;
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
        // solo se mockean las fachadas de los otros módulos. ExcelRowResolver se instancia real
        // (no mock): es un simple orquestador de esas fachadas, la anotación @Transactional no
        // aplica fuera de un contexto Spring.
        ExcelRowResolver rowResolver = new ExcelRowResolver(specialtyService, studyPlanService, subjectService,
            academicPeriodService, commissionService, subjectCommissionService, academicEventService,
            buildingService, classroomService);
        service = new ExcelImportServiceImpl(new ExcelTemplateValidator(), new ExcelRowMapper(),
            rowResolver, allocationService);
    }

    @Test
    @DisplayName("fila válida: busca catálogo y encadena find-or-create de período/find de comisión/materia-comisión con las claves correctas")
    void cadenaCompletaDeResolucion() {
        stubHappyPath(DataRow.defaultRow());
        MockMultipartFile file = ExcelTestWorkbooks.validTemplate(2026).withValidDataRow().toMultipartFile();

        ImportResultDto result = service.importExcel(file);

        assertThat(result.processedRows()).isEqualTo(1);
        assertThat(result.periodsCreated()).isEqualTo(1);

        verify(specialtyService).findBySpecialtyCode(1);
        verify(studyPlanService).findByPlanCodeAndSpecialtyCode(1, 1);
        verify(subjectService).findByCodeAndStudyPlan(100, 1, 1);
        verify(academicPeriodService).findOrCreate(2026, TermType.ANUAL);
        verify(commissionService).findByCourseAndNumberAndPeriod("6301", 1, 2026, 0);
        verify(subjectCommissionService).findBySubjectAndCommission(10L, 20L);
        verify(buildingService).findByName("Edificio Central");
        verify(classroomService).findByRoomNumberAndBuilding("105", 5);

        ArgumentCaptor<CreateRecurringEventRequestDto> eventCaptor =
            ArgumentCaptor.forClass(CreateRecurringEventRequestDto.class);
        verify(academicEventService).findOrCreateRecurringEvent(eventCaptor.capture());
        CreateRecurringEventRequestDto eventDto = eventCaptor.getValue();
        assertThat(eventDto.subjectId()).isEqualTo(10L);
        assertThat(eventDto.commissionId()).isEqualTo(20L);
        assertThat(eventDto.dayOfWeek()).isEqualTo(DayOfWeek.MONDAY);
        assertThat(eventDto.startDate()).isEqualTo(LocalDate.of(2026, 3, 1));
        assertThat(eventDto.endDate()).isEqualTo(LocalDate.of(2026, 11, 30)); // TermType.ANUAL

        ArgumentCaptor<List<AllocateFromDateRequestDto>> allocationCaptor = ArgumentCaptor.forClass(List.class);
        verify(allocationService).importAllocationsBatch(allocationCaptor.capture());
        AllocateFromDateRequestDto allocationDto = allocationCaptor.getValue().getFirst();
        assertThat(allocationDto.recurringEventId()).isEqualTo(1L);
        assertThat(allocationDto.classroomId()).isEqualTo(5);
        assertThat(allocationDto.observation()).isEqualTo("Importado de Excel");
    }

    @Test
    @DisplayName("ImportCache dedupea: dos filas de la misma especialidad → findBySpecialtyCode se llama una sola vez")
    void cacheDedupeaEspecialidadRepetida() {
        SpecialtyResponseDto specialty = new SpecialtyResponseDto(1, "Ingeniería en Sistemas");
        when(specialtyService.findBySpecialtyCode(1)).thenReturn(specialty);
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
        verify(specialtyService, times(1)).findBySpecialtyCode(1);
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

        verify(specialtyService, never()).findBySpecialtyCode(any());
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
        verify(subjectService, times(1)).findByCodeAndStudyPlan(any(), any(), any());
    }

    @Test
    @DisplayName("catálogo no encontrado (p. ej. materia inexistente) → saltea la fila y la reporta, no aborta el import")
    void materiaInexistenteSalteaFilaYLaReporta() {
        SpecialtyResponseDto specialty = new SpecialtyResponseDto(1, "Ingeniería en Sistemas");
        when(specialtyService.findBySpecialtyCode(1)).thenReturn(specialty);
        StudyPlanResponseDto plan = new StudyPlanResponseDto(1, specialty);
        when(studyPlanService.findByPlanCodeAndSpecialtyCode(1, 1)).thenReturn(plan);
        when(subjectService.findByCodeAndStudyPlan(100, 1, 1))
            .thenThrow(ar.edu.utn.frc.siga.common.exception.ResourceNotFoundException.of("Subject", 100));

        MockMultipartFile file = ExcelTestWorkbooks.validTemplate(2026).withValidDataRow().toMultipartFile();

        ImportResultDto result = service.importExcel(file);

        assertThat(result.processedRows()).isZero();
        assertThat(result.skippedRows()).hasSize(1);
        assertThat(result.skippedRows().getFirst().message()).contains("Subject not found with id: 100");
        verify(allocationService).importAllocationsBatch(List.of());
    }

    @Test
    @DisplayName("aula resuelta en un edificio distinto al informado → importa igual pero reporta la advertencia")
    void aulaEnEdificioDistintoReportaWarning() {
        stubHappyPath(DataRow.defaultRow());
        // El aula existe, pero en un edificio distinto al informado por la fila (fallback
        // por número de ClassroomService.findByRoomNumberAndBuilding).
        ClassroomResponseDto classroomEnOtroEdificio = new ClassroomResponseDto(5, "105", 1, 40,
            true, 7, "Otro Edificio", 1, "Aula");
        when(classroomService.findByRoomNumberAndBuilding("105", 5)).thenReturn(classroomEnOtroEdificio);
        MockMultipartFile file = ExcelTestWorkbooks.validTemplate(2026).withValidDataRow().toMultipartFile();

        ImportResultDto result = service.importExcel(file);

        assertThat(result.processedRows()).isEqualTo(1);
        assertThat(result.skippedRows()).isEmpty();
        assertThat(result.rowWarnings()).hasSize(1);
        assertThat(result.rowWarnings().getFirst().message()).contains("Otro Edificio");
    }

    @Test
    @DisplayName("período ya existía → no cuenta como creado")
    void periodoExistenteNoSumaAPeriodsCreated() {
        stubHappyPath(DataRow.defaultRow(), false);
        MockMultipartFile file = ExcelTestWorkbooks.validTemplate(2026).withValidDataRow().toMultipartFile();

        ImportResultDto result = service.importExcel(file);

        assertThat(result.periodsCreated()).isZero();
    }

    @Test
    @DisplayName("el log de inicio muestra el nombre real del archivo subido")
    void logDeInicioMuestraElNombreRealDelArchivo() {
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

        String startLog = appender.list.getFirst().getFormattedMessage();
        assertThat(startLog).contains("planilla-2026.xlsx");
    }

    // ---------- helpers de stubbing ----------

    private void stubHappyPath(DataRow row) {
        stubHappyPath(row, true);
    }

    private void stubHappyPath(DataRow row, boolean created) {
        SpecialtyResponseDto specialty = new SpecialtyResponseDto(row.specialtyCode(), "Ingeniería en Sistemas");
        when(specialtyService.findBySpecialtyCode(row.specialtyCode())).thenReturn(specialty);

        StudyPlanResponseDto plan = new StudyPlanResponseDto(row.studyPlanCode(), specialty);
        when(studyPlanService.findByPlanCodeAndSpecialtyCode(row.studyPlanCode(), row.specialtyCode()))
            .thenReturn(plan);

        SubjectResponseDto subject = new SubjectResponseDto(10L, row.subjectCode(), row.subjectName(),
            row.termType(), plan);
        when(subjectService.findByCodeAndStudyPlan(row.subjectCode(), row.studyPlanCode(), row.specialtyCode()))
            .thenReturn(subject);

        AcademicPeriodResponseDto period = new AcademicPeriodResponseDto(2026, 0,
            LocalDate.of(2026, 3, 1), LocalDate.of(2026, 11, 30));
        when(academicPeriodService.findOrCreate(2026, TermType.ANUAL))
            .thenReturn(new FindOrCreateResult<>(period, created));

        int yearLevel = Character.getNumericValue(row.courseCode().charAt(0));
        CommissionResponseDto commission = new CommissionResponseDto(20L, row.courseCode(), row.commissionNumber(),
            yearLevel, period);
        when(commissionService.findByCourseAndNumberAndPeriod(row.courseCode(), row.commissionNumber(), 2026, 0))
            .thenReturn(commission);

        SubjectCommissionResponseDto subjectCommission = new SubjectCommissionResponseDto(30L, 10L, 20L,
            commission, row.enrolledCount());
        when(subjectCommissionService.findBySubjectAndCommission(10L, 20L))
            .thenReturn(subjectCommission);

        BuildingResponseDto building = new BuildingResponseDto(5, row.buildingName(), 5, true);
        when(buildingService.findByName(row.buildingName())).thenReturn(building);

        ClassroomResponseDto classroom = new ClassroomResponseDto(5, String.valueOf(row.roomNumber()), 1, 40,
            true, 5, row.buildingName(), 1, "Aula");
        when(classroomService.findByRoomNumberAndBuilding(String.valueOf(row.roomNumber()), 5))
            .thenReturn(classroom);

        RecurringEventResponseDto event = new RecurringEventResponseDto(1L, EventType.RECURRING,
            row.enrolledCount(), LocalTime.of(18, 30), 90L, DayOfWeek.MONDAY,
            LocalDate.of(2026, 3, 1), LocalDate.of(2026, 11, 30), subject, commission);
        when(academicEventService.findOrCreateRecurringEvent(any()))
            .thenReturn(new FindOrCreateResult<>(event.id(), true));
    }

    /** Variante de {@link #stubHappyPath} con ids explícitos, usada por el test de dedupe. */
    private void stubRestOfChain(SpecialtyResponseDto specialty, int subjectCode, String subjectName,
            long subjectId, String courseCode, int commissionNumber, long commissionId, String roomNumber,
            int buildingId, long subjectCommissionId, long eventId) {
        StudyPlanResponseDto plan = new StudyPlanResponseDto(1, specialty);
        when(studyPlanService.findByPlanCodeAndSpecialtyCode(1, 1)).thenReturn(plan);

        SubjectResponseDto subject = new SubjectResponseDto(subjectId, subjectCode, subjectName, "Anual", plan);
        when(subjectService.findByCodeAndStudyPlan(subjectCode, 1, 1)).thenReturn(subject);

        AcademicPeriodResponseDto period = new AcademicPeriodResponseDto(2026, 0,
            LocalDate.of(2026, 3, 1), LocalDate.of(2026, 11, 30));
        when(academicPeriodService.findOrCreate(2026, TermType.ANUAL)).thenReturn(new FindOrCreateResult<>(period, true));

        CommissionResponseDto commission = new CommissionResponseDto(commissionId, courseCode, commissionNumber, 6, period);
        when(commissionService.findByCourseAndNumberAndPeriod(courseCode, commissionNumber, 2026, 0))
            .thenReturn(commission);

        SubjectCommissionResponseDto subjectCommission =
            new SubjectCommissionResponseDto(subjectCommissionId, subjectId, commissionId, commission, 30);
        when(subjectCommissionService.findBySubjectAndCommission(subjectId, commissionId))
            .thenReturn(subjectCommission);

        BuildingResponseDto building = new BuildingResponseDto(buildingId, "Edificio Central", 5, true);
        when(buildingService.findByName("Edificio Central")).thenReturn(building);

        ClassroomResponseDto classroom = new ClassroomResponseDto(buildingId, roomNumber, 1, 40, true,
            buildingId, "Edificio Central", 1, "Aula");
        when(classroomService.findByRoomNumberAndBuilding(roomNumber, buildingId)).thenReturn(classroom);

        RecurringEventResponseDto event = new RecurringEventResponseDto(eventId, EventType.RECURRING, 30,
            LocalTime.of(18, 30), 90L, DayOfWeek.MONDAY, LocalDate.of(2026, 3, 1), LocalDate.of(2026, 11, 30),
            subject, commission);
        when(academicEventService.findOrCreateRecurringEvent(any()))
            .thenReturn(new FindOrCreateResult<>(event.id(), true));
    }

    private void stubForSecondSubject(SpecialtyResponseDto specialty) {
        StudyPlanResponseDto plan = new StudyPlanResponseDto(1, specialty);
        SubjectResponseDto subject = new SubjectResponseDto(21L, 101, "Álgebra", "Anual", plan);
        when(subjectService.findByCodeAndStudyPlan(101, 1, 1)).thenReturn(subject);

        // Misma comisión (cacheada de la primera fila, id 30L) pero materia distinta (21L):
        // clave de subjectCommission distinta a la de la primera fila, requiere su propio stub.
        SubjectCommissionResponseDto subjectCommission = new SubjectCommissionResponseDto(41L, 21L, 30L, null, 30);
        when(subjectCommissionService.findBySubjectAndCommission(21L, 30L))
            .thenReturn(subjectCommission);
    }
}
