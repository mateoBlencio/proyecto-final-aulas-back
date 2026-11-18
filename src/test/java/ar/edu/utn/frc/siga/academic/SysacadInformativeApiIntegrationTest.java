package ar.edu.utn.frc.siga.academic;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ar.edu.utn.frc.siga.AbstractIntegrationTest;
import ar.edu.utn.frc.siga.academic.model.Commission;
import ar.edu.utn.frc.siga.academic.model.Subject;
import ar.edu.utn.frc.siga.academic.repository.CommissionRepository;
import ar.edu.utn.frc.siga.events.dto.request.CreateRecurringEventRequestDto;
import ar.edu.utn.frc.siga.testsupport.IntegrationTestData;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import tools.jackson.databind.ObjectMapper;

@Import(IntegrationTestData.class)
@DisplayName("Estado de SysAcad informativo en el catálogo académico (API, integración)")
class SysacadInformativeApiIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private IntegrationTestData testData;
    @Autowired
    private CommissionRepository commissionRepository;
    @Autowired
    private ObjectMapper objectMapper;

    private final List<Long> notCurrentCommissionIds = new ArrayList<>();

    @AfterEach
    void restoreSysacadFlags() {
        notCurrentCommissionIds.forEach(id -> {
            Commission commission = commissionRepository.findById(id).orElseThrow();
            commission.setSysacadEnabled(true);
            commissionRepository.save(commission);
        });
        notCurrentCommissionIds.clear();
    }

    private void markNotCurrent(Long commissionId) {
        Commission commission = commissionRepository.findById(commissionId).orElseThrow();
        commission.setSysacadEnabled(false);
        commission.setSysacadHash("hash-" + commissionId);
        commissionRepository.save(commission);
        notCurrentCommissionIds.add(commissionId);
    }

    @Test
    @DisplayName("GET /v1/commissions lista la comisión fuera de SysAcad con currentInSysacad false y la vigente con true")
    void listCommissions_includesCommissionNotInSysacadFlagged() throws Exception {
        IntegrationTestData.SubjectAndCommission notCurrent = testData.materiaYComision();
        IntegrationTestData.SubjectAndCommission current = testData.materiaYComision();
        markNotCurrent(notCurrent.commissionId());

        mockMvc.perform(get("/v1/commissions").param("subjectId", String.valueOf(notCurrent.subjectId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[?(@.id == " + notCurrent.commissionId() + ")].currentInSysacad")
                        .value(contains(false)));
        mockMvc.perform(get("/v1/commissions").param("subjectId", String.valueOf(current.subjectId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[?(@.id == " + current.commissionId() + ")].currentInSysacad")
                        .value(contains(true)));
    }

    @Test
    @DisplayName("GET /v1/commissions/{id} resuelve la comisión fuera de SysAcad con 200 y sin unavailableReason")
    void getCommissionById_notInSysacad_isResolved() throws Exception {
        IntegrationTestData.SubjectAndCommission academic = testData.materiaYComision();
        markNotCurrent(academic.commissionId());

        mockMvc.perform(get("/v1/commissions/{id}", academic.commissionId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currentInSysacad").value(false))
                .andExpect(jsonPath("$.unavailableReason").value(nullValue()));
    }

    @Test
    @DisplayName("POST /v1/events/recurring acepta una comisión fuera de SysAcad y responde 201")
    void createRecurringEvent_forCommissionNotInSysacad_returns201() throws Exception {
        IntegrationTestData.SubjectAndCommission academic = testData.materiaYComision();
        markNotCurrent(academic.commissionId());
        LocalDate startDate = LocalDate.now().plusDays(1);
        CreateRecurringEventRequestDto dto = new CreateRecurringEventRequestDto(
                30, LocalTime.of(8, 0), 90, startDate.getDayOfWeek(), startDate, startDate.plusWeeks(2),
                academic.subjectId(), academic.commissionId());

        mockMvc.perform(post("/v1/events/recurring")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.type").value("RECURRING"));
    }

    @Test
    @DisplayName("GET /v1/subject-commissions trae el vínculo con enrolledCount 0 activo, sin deactivationReason y con la comisión fuera de SysAcad")
    void listSubjectCommissions_zeroEnrolledIsActiveAndHasNoDeactivationReason() throws Exception {
        IntegrationTestData.SubjectAndCommission academic = testData.materiaYComision();
        Subject subject = testData.materia((int) IntegrationTestData.nextSeq(), "Materia cero", testData.planDeEstudio(
                (int) IntegrationTestData.nextSeq(), testData.especialidad((int) IntegrationTestData.nextSeq())), "Anual");
        testData.materiaComision(subject, commissionRepository.findById(academic.commissionId()).orElseThrow(), 0);
        markNotCurrent(academic.commissionId());

        mockMvc.perform(get("/v1/subject-commissions").param("subjectId", String.valueOf(subject.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].enrolledCount").value(0))
                .andExpect(jsonPath("$.content[0].enabled").value(true))
                .andExpect(jsonPath("$.content[0].unavailableReason").value(nullValue()))
                .andExpect(jsonPath("$.content[0].commission.currentInSysacad").value(false))
                .andExpect(jsonPath("$.content[0].deactivationReason").doesNotExist());
    }
}
