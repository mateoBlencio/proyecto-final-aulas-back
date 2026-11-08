package ar.edu.utn.frc.siga.sysacad;

import ar.edu.utn.frc.siga.sysacad.internal.config.SysacadWriteMode;
import ar.edu.utn.frc.siga.settings.service.SettingsStore;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ar.edu.utn.frc.siga.AbstractIntegrationTest;
import ar.edu.utn.frc.siga.events.model.Occurrence;
import ar.edu.utn.frc.siga.events.repository.OccurrenceRepository;
import ar.edu.utn.frc.siga.roomrequest.model.RoomRequest;
import ar.edu.utn.frc.siga.roomrequest.model.RoomRequestItem;
import ar.edu.utn.frc.siga.roomrequest.model.RoomRequestStatus;
import ar.edu.utn.frc.siga.roomrequest.model.RoomRequestType;
import ar.edu.utn.frc.siga.roomrequest.repository.RoomRequestRepository;
import ar.edu.utn.frc.siga.sysacad.internal.client.SysacadClient;
import ar.edu.utn.frc.siga.sysacad.internal.service.impl.SysacadCatalogSnapshotFactory;
import ar.edu.utn.frc.siga.testsupport.IntegrationTestData;
import ar.edu.utn.frc.siga.testsupport.IntegrationTestData.SubjectAndCommission;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.TemporalAdjusters;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@Import(IntegrationTestData.class)
@DisplayName("API de horarios de SysAcad (integración, flag encendido)")
@TestPropertySource(properties = {
        "siga.sysacad.enabled=true"
})
class SysacadScheduleApiIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private SettingsStore settingsStore;

    @BeforeEach
    void useWriteMode() {
        SysacadModeSupport.use(settingsStore, SysacadWriteMode.DRY_RUN);
    }

    @AfterEach
    void resetWriteMode() {
        SysacadModeSupport.reset(settingsStore);
    }

    private static final int YEAR = 2093;
    private static final String KEEP_BODY = "{\"observation\":\"Lo pidió Subsecretaría\"}";

    @MockitoBean
    private SysacadClient sysacadClient;
    @MockitoBean
    private SysacadCatalogSnapshotFactory snapshotFactory;

    @Autowired
    private IntegrationTestData testData;
    @Autowired
    private OccurrenceRepository occurrenceRepository;
    @Autowired
    private RoomRequestRepository roomRequestRepository;
    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    @AfterEach
    void cleanTestRows() {
        jdbc.update("DELETE FROM sysacad_horario_registro");
        jdbc.update("DELETE FROM sysacad_horario");
    }

    private long insertSchedule(String status, boolean sendable, boolean present, Long eventId) {
        int subject = (int) IntegrationTestData.nextSeq();
        return jdbc.queryForObject("INSERT INTO sysacad_horario (anio, especialidad, plan, materia, comision, dia, "
                + "cuatrimestre, curso, hora_comienzo, hora_fin, duracion, edificio_conocido, aula_conocida, "
                + "edificio_base, aula_base, id_evento_1, estado, enviable, presente_en_vista, motivo_no_enviable, "
                + "motivo_revision, revision_desde, edificio_diferente, aula_diferente) "
                + "VALUES (?, 1, 2008, ?, 7, 1, 1, '1R1', '08:00', '09:30', 90, 5, 228, 5, 228, ?, ?, ?, ?, "
                + "CASE WHEN ? THEN NULL ELSE 'KEY_CONFLICT' END, "
                + "CASE WHEN ? = 'REVIEW' THEN 'ROOM_OCCUPIED' END, "
                + "CASE WHEN ? = 'REVIEW' THEN now() END, "
                + "CASE WHEN ? = 'REVIEW' THEN 5 END, CASE WHEN ? = 'REVIEW' THEN 229 END) RETURNING id_horario",
                Long.class, YEAR, subject, eventId, status, sendable, present, sendable, status, status, status,
                status);
    }

    private long reviewSchedule() {
        return insertSchedule("REVIEW", true, true, null);
    }

    private long waitingSchedule(boolean nextClassWithin24Hours) {
        SubjectAndCommission academic = testData.materiaYComision();
        LocalDate date;
        if (nextClassWithin24Hours) {
            LocalDateTime now = LocalDateTime.now();
            date = now.toLocalTime().isBefore(LocalTime.of(8, 0)) ? now.toLocalDate() : now.toLocalDate().plusDays(1);
        } else {
            date = LocalDate.now().plusYears(1).with(TemporalAdjusters.nextOrSame(DayOfWeek.MONDAY));
        }
        Long eventId = testData.eventoRecurrente(academic.subjectId(), academic.commissionId(), date.getDayOfWeek(),
                LocalTime.of(8, 0), 90, date, date, 30);
        jdbc.update("UPDATE evento_academico SET hash_raw = 'h-' || id_evento_academico, habilitado_sysacad = TRUE "
                + "WHERE id_evento_academico = ?", eventId);
        Occurrence occurrence = occurrenceRepository.findByEvent_Id(eventId).getFirst();
        RoomRequest request = testData.solicitudDeAula(RoomRequestType.ONE_TIME_ROOM_CHANGE, academic.subjectId());
        RoomRequestItem item = testData.itemDePedido(request, academic.commissionId(), date,
                RoomRequestStatus.IN_EVALUATION);
        roomRequestRepository.save(request);
        jdbc.update("INSERT INTO solicitud_item_asignacion (id_item, id_ocurrencia, id_aula, orden) "
                + "VALUES (?, ?, ?, 1)", item.getId(), occurrence.getId(),
                testData.aula(testData.edificio()).getId());
        return insertSchedule("SYNCED", true, true, eventId);
    }

    private void confirmedSend(long scheduleId) {
        jdbc.update("INSERT INTO sysacad_horario_registro (id_horario, fecha, tipo, resultado, edificio_nuevo, "
                + "aula_nuevo) VALUES (?, now(), 'SEND_CONFIRMED', 'CONFIRMED', 5, 228)", scheduleId);
    }

    private MockMvc auxiliar() {
        return mockMvcAsAuxiliar("auxiliar-sysacad@frc.utn.edu.ar");
    }

    @Test
    @DisplayName("GET review: trae REVIEW, no enviables presentes en la vista y esperas a menos de 24 horas, con el último envío confirmado")
    void reviewListsAttentionRows() throws Exception {
        long review = reviewSchedule();
        confirmedSend(review);
        long notSendable = insertSchedule("SYNCED", false, true, null);
        long waitingNear = waitingSchedule(true);

        mockMvc.perform(get("/v1/sysacad/schedules/review").param("size", "50"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[?(@.id==" + review + ")].type").value(contains("REVIEW")))
                .andExpect(jsonPath("$.content[?(@.id==" + review + ")].reason").value(contains("ROOM_OCCUPIED")))
                .andExpect(jsonPath("$.content[?(@.id==" + review + ")].readRoom").value(contains(229)))
                .andExpect(jsonPath("$.content[?(@.id==" + review + ")].lastConfirmedSend.room")
                        .value(contains(228)))
                .andExpect(jsonPath("$.content[?(@.id==" + notSendable + ")].type")
                        .value(contains("NOT_SENDABLE")))
                .andExpect(jsonPath("$.content[?(@.id==" + notSendable + ")].reason")
                        .value(contains("KEY_CONFLICT")))
                .andExpect(jsonPath("$.content[?(@.id==" + waitingNear + ")].type").value(contains("WAITING")))
                .andExpect(jsonPath("$.content[?(@.id==" + waitingNear + ")].blockingItemId").isNotEmpty());
    }

    @Test
    @DisplayName("GET review: no trae una espera con la clase a más de 24 horas ni un no enviable ausente de la vista")
    void reviewOmitsFarWaitingAndAbsentRows() throws Exception {
        long waitingFar = waitingSchedule(false);
        long absent = insertSchedule("SYNCED", false, false, null);
        long review = reviewSchedule();

        mockMvc.perform(get("/v1/sysacad/schedules/review").param("size", "50"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].id").value(hasItem((int) review)))
                .andExpect(jsonPath("$.content[*].id").value(not(hasItem((int) waitingFar))))
                .andExpect(jsonPath("$.content[*].id").value(not(hasItem((int) absent))));
    }

    @Test
    @DisplayName("GET review: pagina el resultado con totalElements y el orden por tipo")
    void reviewPaginates() throws Exception {
        long review = reviewSchedule();
        long notSendableA = insertSchedule("SYNCED", false, true, null);
        long notSendableB = insertSchedule("SYNCED", false, true, null);

        mockMvc.perform(get("/v1/sysacad/schedules/review").param("size", "2").param("page", "0"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page.totalElements").value(3))
                .andExpect(jsonPath("$.content", hasSize(2)))
                .andExpect(jsonPath("$.content[0].id").value((int) review));
        mockMvc.perform(get("/v1/sysacad/schedules/review").param("size", "2").param("page", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].id").value((int) notSendableB));
        org.assertj.core.api.Assertions.assertThat(notSendableA).isLessThan(notSendableB);
    }

    @Test
    @DisplayName("GET {id}/log: devuelve el registro del más nuevo al más viejo; un id inexistente da 404")
    void logReturnsNewestFirstAndUnknownIs404() throws Exception {
        long id = reviewSchedule();
        jdbc.update("INSERT INTO sysacad_horario_registro (id_horario, fecha, tipo) VALUES (?, now(), 'SEND_INTENT')",
                id);
        confirmedSend(id);

        mockMvc.perform(get("/v1/sysacad/schedules/" + id + "/log"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].type").value(contains("SEND_CONFIRMED", "SEND_INTENT")));
        mockMvc.perform(get("/v1/sysacad/schedules/" + (id + 100000) + "/log"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("GET write/status: devuelve el modo, la última corrida y los contadores")
    void statusReturnsModeAndCounters() throws Exception {
        reviewSchedule();

        mockMvc.perform(get("/v1/sysacad/write/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mode").value("DRY_RUN"))
                .andExpect(jsonPath("$.reviewCount").value(1))
                .andExpect(jsonPath("$.waitingCount").value(0));
    }

    @Test
    @DisplayName("POST resend-siga: toma la lectura como conocida, devuelve el horario sin revisión y un segundo intento da 409")
    void resendSigaResolvesAndSecondCallIs409() throws Exception {
        long id = reviewSchedule();

        mockMvc.perform(post("/v1/sysacad/schedules/" + id + "/review/resend-siga"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SYNCED"))
                .andExpect(jsonPath("$.knownRoom").value(229))
                .andExpect(jsonPath("$.baseRoom").value(228));
        mockMvc.perform(post("/v1/sysacad/schedules/" + id + "/review/resend-siga"))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("POST keep-sysacad: cuerpo inválido da 400, horario inexistente 404 y horario fuera de revisión 409")
    void keepSysacadValidatesBodyAndState() throws Exception {
        long review = reviewSchedule();
        long synced = insertSchedule("SYNCED", true, true, null);

        mockMvc.perform(post("/v1/sysacad/schedules/" + review + "/review/keep-sysacad")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"observation\":\"  \"}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/v1/sysacad/schedules/" + review + "/review/keep-sysacad")
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/v1/sysacad/schedules/" + review + "/review/keep-sysacad")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"observation\":\"" + "x".repeat(256) + "\"}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/v1/sysacad/schedules/" + (review + 100000) + "/review/keep-sysacad")
                        .contentType(MediaType.APPLICATION_JSON).content(KEEP_BODY))
                .andExpect(status().isNotFound());
        mockMvc.perform(post("/v1/sysacad/schedules/" + synced + "/review/keep-sysacad")
                        .contentType(MediaType.APPLICATION_JSON).content(KEEP_BODY))
                .andExpect(status().isConflict());
        mockMvc.perform(post("/v1/sysacad/schedules/" + (review + 100000) + "/review/resend-siga"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("AUXILIAR_AULICO recibe 403 en los cinco endpoints")
    void auxiliaryIsForbiddenEverywhere() throws Exception {
        long id = reviewSchedule();
        MockMvc aux = auxiliar();

        aux.perform(get("/v1/sysacad/schedules/review")).andExpect(status().isForbidden());
        aux.perform(get("/v1/sysacad/schedules/" + id + "/log")).andExpect(status().isForbidden());
        aux.perform(get("/v1/sysacad/write/status")).andExpect(status().isForbidden());
        aux.perform(post("/v1/sysacad/schedules/" + id + "/review/resend-siga")).andExpect(status().isForbidden());
        aux.perform(post("/v1/sysacad/schedules/" + id + "/review/keep-sysacad")
                .contentType(MediaType.APPLICATION_JSON).content(KEEP_BODY)).andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("sin token los cinco endpoints rechazan la llamada")
    void anonymousIsRejected() throws Exception {
        MockMvc anonymous = org.springframework.test.web.servlet.setup.MockMvcBuilders
                .webAppContextSetup(webApplicationContextForAnonymous())
                .apply(org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity())
                .build();

        anonymous.perform(get("/v1/sysacad/schedules/review")).andExpect(status().isUnauthorized());
        anonymous.perform(get("/v1/sysacad/write/status")).andExpect(status().isUnauthorized());
        anonymous.perform(post("/v1/sysacad/schedules/1/review/resend-siga")).andExpect(status().isUnauthorized());
    }

    @Autowired
    private org.springframework.web.context.WebApplicationContext context;

    private org.springframework.web.context.WebApplicationContext webApplicationContextForAnonymous() {
        return context;
    }
}
