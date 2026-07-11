package ar.edu.utn.frc.siga.excelimport;

import ar.edu.utn.frc.siga.AbstractIntegrationTest;
import ar.edu.utn.frc.siga.academic.model.TermType;
import ar.edu.utn.frc.siga.academic.repository.AcademicPeriodRepository;
import ar.edu.utn.frc.siga.academic.repository.CommissionRepository;
import ar.edu.utn.frc.siga.academic.repository.SpecialtyRepository;
import ar.edu.utn.frc.siga.academic.repository.StudyPlanRepository;
import ar.edu.utn.frc.siga.academic.repository.SubjectRepository;
import ar.edu.utn.frc.siga.allocation.model.Allocation;
import ar.edu.utn.frc.siga.allocation.model.AllocationSource;
import ar.edu.utn.frc.siga.allocation.model.Occurrence;
import ar.edu.utn.frc.siga.allocation.model.OccurrenceStatus;
import ar.edu.utn.frc.siga.allocation.repository.AcademicEventRepository;
import ar.edu.utn.frc.siga.allocation.repository.AllocationRepository;
import ar.edu.utn.frc.siga.allocation.repository.OccurrenceRepository;
import ar.edu.utn.frc.siga.excelimport.ExcelTestWorkbooks.DataRow;
import ar.edu.utn.frc.siga.excelimport.dto.ImportResultDto;
import ar.edu.utn.frc.siga.space.model.Classroom;
import ar.edu.utn.frc.siga.space.repository.BuildingRepository;
import ar.edu.utn.frc.siga.space.repository.ClassroomRepository;
import ar.edu.utn.frc.siga.testsupport.IntegrationTestData;

import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MvcResult;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integración end-to-end de la carga masiva desde Excel: sube un {@code .xlsx} en memoria
 * (fixture {@link ExcelTestWorkbooks}) contra Postgres real y verifica la cadena completa
 * (specialty → studyPlan → subject → period → commission → classroom → evento →
 * ocurrencias → asignaciones IMPORTED), idempotencia, y atomicidad de la transacción única.
 */
@Import(IntegrationTestData.class)
@DisplayName("Excel Import (integración)")
class ExcelImportIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private SpecialtyRepository specialtyRepository;
    @Autowired
    private StudyPlanRepository studyPlanRepository;
    @Autowired
    private SubjectRepository subjectRepository;
    @Autowired
    private CommissionRepository commissionRepository;
    @Autowired
    private AcademicPeriodRepository academicPeriodRepository;
    @Autowired
    private BuildingRepository buildingRepository;
    @Autowired
    private ClassroomRepository classroomRepository;
    @Autowired
    private AcademicEventRepository eventRepository;
    @Autowired
    private OccurrenceRepository occurrenceRepository;
    @Autowired
    private AllocationRepository allocationRepository;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    /** Fila válida con claves naturales únicas (courseCode arranca con '6', año fijo de la plantilla = 2026). */
    private DataRow uniqueRow(String buildingName) {
        long seq = IntegrationTestData.nextSeq();
        int code = (int) seq;
        return DataRow.builder()
                .courseCode("6" + seq)
                .commissionNumber(1)
                .roomNumber("AULA-" + seq)
                .buildingName(buildingName)
                .day("Lunes")
                .termType("Anual")
                .startTime(1830)
                .endTime(2000)
                .durationMinutes(null)
                .specialtyCode(code)
                .studyPlanCode(code)
                .subjectCode(code)
                .subjectName("Materia-IT-" + seq)
                .enrolledCount(5)
                .build();
    }

    private String uniqueBuilding() {
        return "Edificio-IT-" + IntegrationTestData.nextSeq();
    }

    @Test
    @DisplayName("POST /v1/excelimports importa 2 filas válidas y persiste toda la cadena académica y de asignación, incluidas fechas pasadas")
    void importExcel_validRows_persistsFullChain() throws Exception {
        DataRow row1 = uniqueRow(uniqueBuilding());
        DataRow row2 = uniqueRow(uniqueBuilding());

        long specialtiesBefore = specialtyRepository.count();
        long studyPlansBefore = studyPlanRepository.count();
        long subjectsBefore = subjectRepository.count();
        long commissionsBefore = commissionRepository.count();
        long buildingsBefore = buildingRepository.count();
        long classroomsBefore = classroomRepository.count();
        long eventsBefore = eventRepository.count();

        var workbook = ExcelTestWorkbooks.validTemplate().withDataRow(row1).withDataRow(row2);

        MvcResult result = mockMvc.perform(multipart("/v1/excelimports").file(workbook.toMultipartFile()))
                .andExpect(status().isOk())
                .andReturn();

        ImportResultDto response = objectMapper.readValue(
                result.getResponse().getContentAsString(), ImportResultDto.class);
        assertThat(response.processedRows()).isEqualTo(2);
        assertThat(response.assignmentsCreated()).isEqualTo(2);
        assertThat(response.assignmentsReused()).isZero();

        assertThat(specialtyRepository.count()).isEqualTo(specialtiesBefore + 2);
        assertThat(studyPlanRepository.count()).isEqualTo(studyPlansBefore + 2);
        assertThat(subjectRepository.count()).isEqualTo(subjectsBefore + 2);
        assertThat(commissionRepository.count()).isEqualTo(commissionsBefore + 2);
        assertThat(buildingRepository.count()).isEqualTo(buildingsBefore + 2);
        assertThat(classroomRepository.count()).isEqualTo(classroomsBefore + 2);
        assertThat(eventRepository.count()).isEqualTo(eventsBefore + 2);
        assertThat(academicPeriodRepository.findByYearAndSemester(2026, TermType.ANUAL.getSemester())).isPresent();

        // Cadena completa de la fila 1: aula creada -> evento -> ocurrencias -> asignaciones IMPORTED.
        Classroom classroom1 = classroomRepository.findByRoomNumber((String) row1.roomNumber()).orElseThrow();
        assertThat(classroom1.getCapacity()).isEqualTo(row1.enrolledCount());

        Long eventId1 = jdbcTemplate.queryForObject(
                "SELECT o.id_evento_academico FROM ocurrencia o " +
                        "JOIN asignacion_aula a ON a.id_ocurrencia = o.id_ocurrencia " +
                        "WHERE a.id_aula = ? LIMIT 1",
                Long.class, classroom1.getId());
        assertThat(eventRepository.existsById(eventId1)).isTrue();

        List<Occurrence> occurrences1 = occurrenceRepository.findByEvent_Id(eventId1);
        assertThat(occurrences1).isNotEmpty();
        assertThat(occurrences1).allSatisfy(o -> assertThat(o.getStatus()).isEqualTo(OccurrenceStatus.ASSIGNED));
        // Año 2026 de la plantilla con "hoy" en julio de 2026: el rango Anual (marzo-noviembre) ya generó fechas pasadas.
        assertThat(occurrences1).anySatisfy(o -> assertThat(o.getDate()).isBefore(LocalDate.now()));

        List<Allocation> allocations1 = allocationRepository.findByOccurrence_IdIn(
                occurrences1.stream().map(Occurrence::getId).toList());
        assertThat(allocations1).hasSameSizeAs(occurrences1);
        assertThat(allocations1).allSatisfy(a -> {
            assertThat(a.getSource()).isEqualTo(AllocationSource.IMPORTED);
            assertThat(a.getClassroomId()).isEqualTo(classroom1.getId());
        });
    }

    @Test
    @DisplayName("Reimportar el mismo archivo es idempotente: reusa entidades y asignaciones, sin duplicar")
    void importExcel_sameFileTwice_isIdempotent() throws Exception {
        DataRow row = uniqueRow(uniqueBuilding());
        MockMultipartFile file = ExcelTestWorkbooks.validTemplate().withDataRow(row).toMultipartFile();

        mockMvc.perform(multipart("/v1/excelimports").file(file)).andExpect(status().isOk());

        long specialtiesAfterFirst = specialtyRepository.count();
        long eventsAfterFirst = eventRepository.count();
        long occurrencesAfterFirst = occurrenceRepository.count();
        long allocationsAfterFirst = allocationRepository.count();
        long classroomsAfterFirst = classroomRepository.count();

        MvcResult second = mockMvc.perform(multipart("/v1/excelimports").file(file))
                .andExpect(status().isOk())
                .andReturn();

        ImportResultDto response = objectMapper.readValue(
                second.getResponse().getContentAsString(), ImportResultDto.class);
        assertThat(response.processedRows()).isEqualTo(1);
        assertThat(response.assignmentsCreated()).isZero();
        assertThat(response.assignmentsReused()).isEqualTo(1);

        assertThat(specialtyRepository.count()).isEqualTo(specialtiesAfterFirst);
        assertThat(eventRepository.count()).isEqualTo(eventsAfterFirst);
        assertThat(occurrenceRepository.count()).isEqualTo(occurrencesAfterFirst);
        assertThat(allocationRepository.count()).isEqualTo(allocationsAfterFirst);
        assertThat(classroomRepository.count()).isEqualTo(classroomsAfterFirst);
    }

    @Test
    @DisplayName("Header roto (hoja 'Hoja1' inexistente) responde 400 y no persiste nada")
    void importExcel_brokenHeader_returns400AndPersistsNothing() throws Exception {
        long eventsBefore = eventRepository.count();
        long classroomsBefore = classroomRepository.count();
        long buildingsBefore = buildingRepository.count();

        var workbook = ExcelTestWorkbooks.validTemplate()
                .withDataRow(uniqueRow(uniqueBuilding()))
                .renameSheet("OtraHoja");

        mockMvc.perform(multipart("/v1/excelimports").file(workbook.toMultipartFile()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Invalid file format"));

        assertThat(eventRepository.count()).isEqualTo(eventsBefore);
        assertThat(classroomRepository.count()).isEqualTo(classroomsBefore);
        assertThat(buildingRepository.count()).isEqualTo(buildingsBefore);
    }

    @Test
    @DisplayName("Fila 2 inválida (día inexistente) revierte también la fila 1 ya procesada: transacción única")
    void importExcel_secondRowInvalid_rollsBackFirstRow() throws Exception {
        DataRow validRow = uniqueRow(uniqueBuilding());
        DataRow invalidRow = uniqueRow(uniqueBuilding()).toBuilder().day("Marciano").build();

        long eventsBefore = eventRepository.count();
        long classroomsBefore = classroomRepository.count();
        long specialtiesBefore = specialtyRepository.count();

        var workbook = ExcelTestWorkbooks.validTemplate().withDataRow(validRow).withDataRow(invalidRow);

        mockMvc.perform(multipart("/v1/excelimports").file(workbook.toMultipartFile()))
                .andExpect(status().is(422))
                .andExpect(jsonPath("$.title").value("Import error"));

        assertThat(eventRepository.count()).isEqualTo(eventsBefore);
        assertThat(classroomRepository.count()).isEqualTo(classroomsBefore);
        assertThat(specialtyRepository.count()).isEqualTo(specialtiesBefore);
        assertThat(classroomRepository.findByRoomNumber((String) validRow.roomNumber())).isEmpty();
    }
}
