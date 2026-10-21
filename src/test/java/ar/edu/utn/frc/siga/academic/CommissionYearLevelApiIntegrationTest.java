package ar.edu.utn.frc.siga.academic;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ar.edu.utn.frc.siga.AbstractIntegrationTest;
import ar.edu.utn.frc.siga.academic.model.AcademicPeriod;
import ar.edu.utn.frc.siga.academic.model.Commission;
import ar.edu.utn.frc.siga.academic.model.TermType;
import ar.edu.utn.frc.siga.testsupport.IntegrationTestData;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;

@Import(IntegrationTestData.class)
@DisplayName("Commission yearLevel en la API (integración)")
class CommissionYearLevelApiIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private IntegrationTestData testData;

    private AcademicPeriod period() {
        return testData.periodoAcademico(2100 + (int) (IntegrationTestData.nextSeq() % 500), TermType.ANUAL);
    }

    @Test
    @DisplayName("GET /v1/commissions/{id} serializa yearLevel con el primer dígito del código de curso")
    void getById_serializesYearLevel() throws Exception {
        Commission commission = testData.comision("3K1A", period());

        mockMvc.perform(get("/v1/commissions/{id}", commission.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.courseCode").value("3K1A"))
                .andExpect(jsonPath("$.yearLevel").value(3));
    }

    @Test
    @DisplayName("GET /v1/commissions/{id} con código que empieza con letra devuelve yearLevel null")
    void getById_lettersFirst_yearLevelIsNull() throws Exception {
        Commission commission = testData.comision("K1001", period());

        mockMvc.perform(get("/v1/commissions/{id}", commission.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.yearLevel").hasJsonPath())
                .andExpect(jsonPath("$.yearLevel").value(org.hamcrest.Matchers.nullValue()));
    }
}
