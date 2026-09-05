package ar.edu.utn.frc.siga.academic;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ar.edu.utn.frc.siga.AbstractIntegrationTest;
import ar.edu.utn.frc.siga.academic.model.AcademicPeriod;
import ar.edu.utn.frc.siga.academic.model.Commission;
import ar.edu.utn.frc.siga.academic.model.Specialty;
import ar.edu.utn.frc.siga.academic.model.StudyPlan;
import ar.edu.utn.frc.siga.academic.model.Subject;
import ar.edu.utn.frc.siga.academic.model.SubjectCommissionId;
import ar.edu.utn.frc.siga.academic.model.TermType;
import ar.edu.utn.frc.siga.academic.repository.AcademicPeriodRepository;
import ar.edu.utn.frc.siga.academic.repository.CommissionRepository;
import ar.edu.utn.frc.siga.academic.repository.StudyPlanRepository;
import ar.edu.utn.frc.siga.academic.repository.SubjectCommissionRepository;
import ar.edu.utn.frc.siga.academic.repository.SubjectRepository;
import ar.edu.utn.frc.siga.auth.model.Role;
import ar.edu.utn.frc.siga.auth.security.JwtService;
import ar.edu.utn.frc.siga.testsupport.IntegrationTestData;
import java.util.Set;
import java.util.function.BooleanSupplier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

@Import(IntegrationTestData.class)
@DisplayName("Academic – endpoints de activación (integración)")
class AcademicActivationApiIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private IntegrationTestData testData;

    @Autowired
    private WebApplicationContext webApplicationContext;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private SubjectRepository subjectRepository;

    @Autowired
    private StudyPlanRepository studyPlanRepository;

    @Autowired
    private CommissionRepository commissionRepository;

    @Autowired
    private AcademicPeriodRepository academicPeriodRepository;

    @Autowired
    private SubjectCommissionRepository subjectCommissionRepository;

    private MockMvc auxiliarMockMvc;

    @BeforeEach
    void setUpAuxiliarMockMvc() {
        String token = jwtService.generateAccessToken(
                "auxiliar@frc.utn.edu.ar", Set.of(Role.AUXILIAR_AULICO));
        auxiliarMockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext)
                .apply(springSecurity())
                .defaultRequest(get("/").header("Authorization", "Bearer " + token))
                .build();
    }

    @Test
    @DisplayName("Subject: PUT/DELETE /v1/subjects/{id}/activation (204, idempotente, 404, 403)")
    void subject_activationLifecycle() throws Exception {
        StudyPlan plan = testData.planDeEstudio((int) IntegrationTestData.nextSeq(), newSpecialty());
        Subject subject = testData.materia((int) IntegrationTestData.nextSeq(), "Materia-Act",
                plan, TermType.ANUAL.getLabel());
        long id = subject.getId();
        assertActivationLifecycle(
                "/v1/subjects/" + id + "/activation",
                "/v1/subjects/999999999/activation",
                () -> subjectRepository.findActiveById(id).isPresent());
    }

    @Test
    @DisplayName("StudyPlan: PUT/DELETE /v1/study-plans/{id}/activation (204, idempotente, 404, 403)")
    void studyPlan_activationLifecycle() throws Exception {
        StudyPlan plan = testData.planDeEstudio((int) IntegrationTestData.nextSeq(), newSpecialty());
        long id = plan.getId();
        assertActivationLifecycle(
                "/v1/study-plans/" + id + "/activation",
                "/v1/study-plans/999999999/activation",
                () -> studyPlanRepository.findActiveById(id).isPresent());
    }

    @Test
    @DisplayName("Commission: PUT/DELETE /v1/commissions/{id}/activation (204, idempotente, 404, 403)")
    void commission_activationLifecycle() throws Exception {
        AcademicPeriod period = testData.periodoAcademico(newYear(), TermType.ANUAL);
        Commission commission = testData.comision("CUR-" + IntegrationTestData.nextSeq(), period);
        long id = commission.getId();
        assertActivationLifecycle(
                "/v1/commissions/" + id + "/activation",
                "/v1/commissions/999999999/activation",
                () -> commissionRepository.findActiveById(id).isPresent());
    }

    @Test
    @DisplayName("AcademicPeriod: PUT/DELETE /v1/academic-periods/{id}/activation (204, idempotente, 404, 403)")
    void academicPeriod_activationLifecycle() throws Exception {
        AcademicPeriod period = testData.periodoAcademico(newYear(), TermType.ANUAL);
        long id = period.getId();
        assertActivationLifecycle(
                "/v1/academic-periods/" + id + "/activation",
                "/v1/academic-periods/999999999/activation",
                () -> academicPeriodRepository.findActiveById(id).isPresent());
    }

    @Test
    @DisplayName("SubjectCommission: PUT/DELETE /v1/subject-commissions/{sid}/{cid}/activation (204, idempotente, 404, 403)")
    void subjectCommission_activationLifecycle() throws Exception {
        IntegrationTestData.SubjectAndCommission link = testData.materiaYComision();
        SubjectCommissionId key = new SubjectCommissionId(link.subjectId(), link.commissionId());
        assertActivationLifecycle(
                "/v1/subject-commissions/" + link.subjectId() + "/" + link.commissionId() + "/activation",
                "/v1/subject-commissions/999999999/999999999/activation",
                () -> subjectCommissionRepository.findActiveById(key).isPresent());
    }

    private Specialty newSpecialty() {
        return testData.especialidad((int) IntegrationTestData.nextSeq());
    }

    private static int newYear() {
        return 2100 + (int) (IntegrationTestData.nextSeq() % 500);
    }

    private void assertActivationLifecycle(String activationPath, String missingPath, BooleanSupplier active)
            throws Exception {
        assertThat(active.getAsBoolean()).isTrue();

        mockMvc.perform(delete(activationPath)).andExpect(status().isNoContent());
        assertThat(active.getAsBoolean()).isFalse();
        mockMvc.perform(delete(activationPath)).andExpect(status().isNoContent());
        assertThat(active.getAsBoolean()).isFalse();

        mockMvc.perform(put(activationPath)).andExpect(status().isNoContent());
        assertThat(active.getAsBoolean()).isTrue();
        mockMvc.perform(put(activationPath)).andExpect(status().isNoContent());
        assertThat(active.getAsBoolean()).isTrue();

        mockMvc.perform(put(missingPath)).andExpect(status().isNotFound());
        mockMvc.perform(delete(missingPath)).andExpect(status().isNotFound());

        auxiliarMockMvc.perform(put(activationPath)).andExpect(status().isForbidden());
        auxiliarMockMvc.perform(delete(activationPath)).andExpect(status().isForbidden());
    }
}
