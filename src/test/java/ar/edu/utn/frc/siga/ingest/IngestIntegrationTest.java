package ar.edu.utn.frc.siga.ingest;

import ar.edu.utn.frc.siga.AbstractIntegrationTest;
import ar.edu.utn.frc.siga.academic.model.AcademicPeriod;
import ar.edu.utn.frc.siga.academic.model.Commission;
import ar.edu.utn.frc.siga.academic.model.Subject;
import ar.edu.utn.frc.siga.academic.model.TermType;
import ar.edu.utn.frc.siga.academic.repository.AcademicPeriodRepository;
import ar.edu.utn.frc.siga.academic.repository.CommissionRepository;
import ar.edu.utn.frc.siga.academic.repository.SpecialtyRepository;
import ar.edu.utn.frc.siga.allocation.model.Allocation;
import ar.edu.utn.frc.siga.allocation.model.AllocationSource;
import ar.edu.utn.frc.siga.events.model.Occurrence;
import ar.edu.utn.frc.siga.events.model.OccurrenceStatus;
import ar.edu.utn.frc.siga.events.repository.AcademicEventRepository;
import ar.edu.utn.frc.siga.allocation.repository.AllocationRepository;
import ar.edu.utn.frc.siga.events.repository.OccurrenceRepository;
import ar.edu.utn.frc.siga.ingest.ExcelTestWorkbooks.DataRow;
import ar.edu.utn.frc.siga.ingest.dto.IngestResultDto;
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
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MvcResult;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Import(IntegrationTestData.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_CLASS)
@DisplayName("Excel Import (integración)")
class IngestIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private SpecialtyRepository specialtyRepository;
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
    @Autowired
    private IntegrationTestData integrationTestData;

    private DataRow uniqueRow(String buildingName) {
        long seq = IntegrationTestData.nextSeq();
        int code = (int) seq;
        return DataRow.builder()
                .courseCode("6" + seq)
                .commissionNumber(1)
                .roomNumber(code)
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

    private void seedCatalog(DataRow row) {
        var specialty = integrationTestData.especialidad(row.specialtyCode());
        var plan = integrationTestData.planDeEstudio(row.studyPlanCode(), specialty);
        Subject subject = integrationTestData.materia(row.subjectCode(), row.subjectName(), plan, row.termType());
        var building = integrationTestData.edificioConNombre(row.buildingName());
        integrationTestData.aulaConNumero((Integer) row.roomNumber(), building,
                integrationTestData.tipoAulaNormal(), row.enrolledCount());

        int year = 2026;
        TermType termType = TermType.fromLabel(row.termType()).orElseThrow();
        AcademicPeriod period = integrationTestData.periodoAcademico(year, termType);
        Commission commission = integrationTestData.comision(row.courseCode(), period);
        integrationTestData.materiaComision(subject, commission, row.enrolledCount());
        integrationTestData.eventoRecurrente(subject.getId(), commission.getId(), DayOfWeek.MONDAY,
                LocalTime.of(18, 30), 90, termType.startDate(year), termType.endDate(year), row.enrolledCount());
    }

    @Test
    @DisplayName("POST /v1/imports importa 2 filas válidas y persiste toda la cadena académica y de asignación, incluidas fechas pasadas")
    void importExcel_validRows_persistsFullChain() throws Exception {
        DataRow row1 = uniqueRow(uniqueBuilding());
        DataRow row2 = uniqueRow(uniqueBuilding());
        seedCatalog(row1);
        seedCatalog(row2);

        long commissionsBefore = commissionRepository.count();
        long eventsBefore = eventRepository.count();

        var workbook = ExcelTestWorkbooks.validTemplate().withDataRow(row1).withDataRow(row2);

        MvcResult result = mockMvc.perform(multipart("/v1/imports").file(workbook.toMultipartFile()))
                .andExpect(status().isOk())
                .andReturn();

        IngestResultDto response = objectMapper.readValue(
                result.getResponse().getContentAsString(), IngestResultDto.class);
        assertThat(response.processedRows()).isEqualTo(2);

        assertThat(commissionRepository.count()).isEqualTo(commissionsBefore);
        assertThat(eventRepository.count()).isEqualTo(eventsBefore);
        assertThat(academicPeriodRepository.findByYearAndSemester(2026, TermType.ANUAL.getSemester())).isPresent();

        Classroom classroom1 = classroomRepository.findByRoomNumberAndDeletedAtIsNull((Integer) row1.roomNumber()).orElseThrow();
        assertThat(classroom1.getCapacity()).isEqualTo(row1.enrolledCount());

        Long eventId1 = jdbcTemplate.queryForObject(
                "SELECT o.id_evento_academico FROM ocurrencia o " +
                        "JOIN asignacion_aula a ON a.id_ocurrencia = o.id_ocurrencia " +
                        "WHERE a.id_aula = ? LIMIT 1",
                Long.class, classroom1.getId());
        assert eventId1 != null;
        assertThat(eventRepository.existsById(eventId1)).isTrue();

        List<Occurrence> occurrences1 = occurrenceRepository.findByEvent_Id(eventId1);
        assertThat(occurrences1).isNotEmpty();
        assertThat(occurrences1).allSatisfy(o -> assertThat(o.getStatus()).isEqualTo(OccurrenceStatus.NEEDS_ROOM));
        assertThat(occurrences1).anySatisfy(o -> assertThat(o.getDate()).isBefore(LocalDate.now()));

        List<Allocation> allocations1 = allocationRepository.findByOccurrenceIdIn(
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
        seedCatalog(row);
        MockMultipartFile file = ExcelTestWorkbooks.validTemplate().withDataRow(row).toMultipartFile();

        mockMvc.perform(multipart("/v1/imports").file(file)).andExpect(status().isOk());

        long specialtiesAfterFirst = specialtyRepository.count();
        long eventsAfterFirst = eventRepository.count();
        long occurrencesAfterFirst = occurrenceRepository.count();
        long allocationsAfterFirst = allocationRepository.count();
        long classroomsAfterFirst = classroomRepository.count();

        MvcResult second = mockMvc.perform(multipart("/v1/imports").file(file))
                .andExpect(status().isOk())
                .andReturn();

        IngestResultDto response = objectMapper.readValue(
                second.getResponse().getContentAsString(), IngestResultDto.class);
        assertThat(response.processedRows()).isEqualTo(1);

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

        mockMvc.perform(multipart("/v1/imports").file(workbook.toMultipartFile()))
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
        seedCatalog(validRow);
        seedCatalog(invalidRow);

        long eventsBefore = eventRepository.count();

        var workbook = ExcelTestWorkbooks.validTemplate().withDataRow(validRow).withDataRow(invalidRow);

        mockMvc.perform(multipart("/v1/imports").file(workbook.toMultipartFile()))
                .andExpect(status().is(422))
                .andExpect(jsonPath("$.title").value("Import error"));

        assertThat(eventRepository.count()).isEqualTo(eventsBefore);
    }
}
