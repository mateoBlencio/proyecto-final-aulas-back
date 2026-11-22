package ar.edu.utn.frc.siga.audit;

import ar.edu.utn.frc.siga.AbstractIntegrationTest;
import ar.edu.utn.frc.siga.academic.model.AcademicPeriod;
import ar.edu.utn.frc.siga.academic.model.StudyPlan;
import ar.edu.utn.frc.siga.academic.model.Subject;
import ar.edu.utn.frc.siga.academic.model.TermType;
import ar.edu.utn.frc.siga.academic.repository.AcademicPeriodRepository;
import ar.edu.utn.frc.siga.academic.service.SubjectService;
import ar.edu.utn.frc.siga.events.model.Occurrence;
import ar.edu.utn.frc.siga.events.repository.OccurrenceRepository;
import ar.edu.utn.frc.siga.testsupport.IntegrationTestData;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Descriptions of real operations as the audit log shows them: base text, the records the operation changed with
 * their summary labels, and the release reason by its Spanish name. Everything goes through services or the API,
 * the real aspect, the Envers tables and the label providers; nothing is stubbed.
 */
@Import(IntegrationTestData.class)
@DisplayName("Descripción de operaciones reales en la auditoría (integración)")
class AuditDescriptionEndToEndIntegrationTest extends AbstractIntegrationTest {

    private static final AtomicInteger NEXT_YEAR = new AtomicInteger(4600);

    @Autowired
    private IntegrationTestData testData;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private SubjectService subjectService;
    @Autowired
    private OccurrenceRepository occurrenceRepository;
    @Autowired
    private AcademicPeriodRepository academicPeriodRepository;

    private record Course(Long subjectId, Long eventId, Long occurrenceId) {}

    /** A period of its own, so the commission codes of this test do not collide with other tests. */
    private AcademicPeriod ownPeriod() {
        AcademicPeriod period = testData.periodoAcademico(NEXT_YEAR.getAndIncrement(), TermType.ANUAL);
        period.setStartDate(LocalDate.now().minusYears(5));
        period.setEndDate(LocalDate.now().plusYears(15));
        return academicPeriodRepository.save(period);
    }

    private Course course(AcademicPeriod period, String subjectName, int subjectCode, String courseCode, int daysAhead) {
        StudyPlan plan = testData.planDeEstudio((int) IntegrationTestData.nextSeq(),
                testData.especialidad((int) IntegrationTestData.nextSeq()));
        Subject subject = testData.materia(subjectCode, subjectName, plan, TermType.ANUAL.getLabel());
        var commission = testData.comision(courseCode, period);
        testData.materiaComision(subject, commission, 30);
        LocalDate date = LocalDate.now().plusDays(daysAhead);
        Long eventId = asFixtureUser(() -> testData.eventoRecurrente(subject.getId(), commission.getId(),
                date.getDayOfWeek(), LocalTime.of(8, 0), 90, date, date, 30));
        Long occurrenceId = occurrenceRepository.findByEvent_Id(eventId).stream()
                .map(Occurrence::getId).findFirst().orElseThrow();
        return new Course(subject.getId(), eventId, occurrenceId);
    }

    private String lastDescriptionOf(String auditTable, String idColumn, Long id, String like) {
        return jdbc.queryForObject("SELECT r.descripcion FROM revinfo r JOIN " + auditTable + " a ON a.rev = r.rev "
                + "WHERE a." + idColumn + " = ? AND r.descripcion LIKE ? ORDER BY r.rev DESC LIMIT 1",
                String.class, id, like);
    }

    @Test
    @DisplayName("baja de una materia: 'Baja de materia: Física I (1)'")
    void subjectDeactivation_namesTheSubject() {
        StudyPlan plan = testData.planDeEstudio((int) IntegrationTestData.nextSeq(),
                testData.especialidad((int) IntegrationTestData.nextSeq()));
        Subject subject = testData.materia(1, "Física I", plan, TermType.ANUAL.getLabel());

        asFixtureUser(() -> subjectService.deactivate(subject.getId()));

        assertThat(lastDescriptionOf("materia_aud", "id_materia", subject.getId(), "Baja de materia%"))
                .isEqualTo("Baja de materia: Física I (1)");
    }

    @Test
    @DisplayName("asignación de aula: 'Asignación: Análisis Matemático I (1K1)', sin conteos de aulas ni edificios")
    void allocation_namesSubjectAndCommission() throws Exception {
        Course course = course(ownPeriod(), "Análisis Matemático I", 1, "1K1", 60);
        Long classroomId = testData.aula(testData.edificio()).getId();

        mockMvc.perform(post("/v1/allocations").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"items\":[{\"eventId\":" + course.eventId() + ",\"classroomId\":" + classroomId + "}]}"))
                .andExpect(status().isCreated());

        assertThat(lastDescriptionOf("asignacion_aula_aud", "id_ocurrencia", course.occurrenceId(), "Asignación%"))
                .isEqualTo("Asignación: Análisis Matemático I (1K1)");
    }

    @Test
    @DisplayName("liberación con motivo: 'Liberación de aulas (Clase virtual): Análisis Matemático I (1K1)'")
    void releaseWithReason_showsTheReasonLabelAndTheEvent() throws Exception {
        Course course = course(ownPeriod(), "Análisis Matemático I", 1, "1K1", 61);
        Long classroomId = testData.aula(testData.edificio()).getId();
        mockMvc.perform(post("/v1/allocations").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"items\":[{\"eventId\":" + course.eventId() + ",\"classroomId\":" + classroomId + "}]}"))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/v1/allocations/releases").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"items\":[{\"occurrenceIds\":[" + course.occurrenceId() + "]}],"
                                + "\"reason\":\"VIRTUAL_CLASS\",\"note\":\"cursado virtual\"}"))
                .andExpect(status().isOk());

        assertThat(lastDescriptionOf("ocurrencia_aud", "id_ocurrencia", course.occurrenceId(), "Liberación%"))
                .isEqualTo("Liberación de aulas (Clase virtual): Análisis Matemático I (1K1)");
    }

    @Test
    @DisplayName("cuatro eventos en una operación: dos nombres en orden del español y 'y 2 más'")
    void severalRecords_twoNamesAndMore() throws Exception {
        AcademicPeriod period = ownPeriod();
        Course dibujo = course(period, "Dibujo", 4, "1K4", 62);
        Course calculo = course(period, "Cálculo", 3, "1K3", 63);
        Course biologia = course(period, "Biología", 2, "1K2", 64);
        Course algebra = course(period, "Álgebra", 1, "1K1", 65);
        List<Long> ids = List.of(dibujo.occurrenceId(), calculo.occurrenceId(), biologia.occurrenceId(),
                algebra.occurrenceId());

        mockMvc.perform(post("/v1/allocations/releases").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"items\":[{\"occurrenceIds\":" + ids + "}],\"reason\":\"CANCELLED\"}"))
                .andExpect(status().isOk());

        assertThat(lastDescriptionOf("ocurrencia_aud", "id_ocurrencia", algebra.occurrenceId(), "Liberación%"))
                .isEqualTo("Liberación de aulas (Cancelada): Álgebra (1K1), Biología (1K2) y 2 más");
    }
}
