package ar.edu.utn.frc.siga.academic;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ar.edu.utn.frc.siga.AbstractIntegrationTest;
import ar.edu.utn.frc.siga.academic.model.AcademicPeriod;
import ar.edu.utn.frc.siga.academic.model.TermType;
import ar.edu.utn.frc.siga.academic.repository.AcademicPeriodRepository;
import ar.edu.utn.frc.siga.auth.model.Role;
import ar.edu.utn.frc.siga.auth.security.JwtService;
import ar.edu.utn.frc.siga.testsupport.IntegrationTestData;
import java.time.LocalDate;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

@Import(IntegrationTestData.class)
@DisplayName("PUT /v1/academic-periods/{id} (integración)")
class AcademicPeriodUpdateApiIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private IntegrationTestData testData;
    @Autowired
    private AcademicPeriodRepository academicPeriodRepository;
    @Autowired
    private WebApplicationContext webApplicationContext;
    @Autowired
    private JwtService jwtService;

    private MockMvc auxiliarMockMvc;
    private int year;

    @BeforeEach
    void setUp() {
        year = 2400 + (int) (IntegrationTestData.nextSeq() % 300);
        testData.periodoAcademico(year, TermType.ANUAL);
        testData.periodoAcademico(year, TermType.PRIMER_CUATRIMESTRE);
        testData.periodoAcademico(year, TermType.SEGUNDO_CUATRIMESTRE);

        String token = jwtService.generateAccessToken("auxiliar@frc.utn.edu.ar", Set.of(Role.AUXILIAR_AULICO));
        auxiliarMockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext)
                .apply(springSecurity())
                .defaultRequest(get("/").header("Authorization", "Bearer " + token))
                .build();
    }

    private Long periodId(TermType termType) {
        return academicPeriodRepository.findByYearAndSemester(year, termType.getSemester()).orElseThrow().getId();
    }

    @Test
    @DisplayName("SUBSECRETARIA setea el receso en el ANUAL y sincroniza 1C/2C")
    void subsecretariaSetsRecess() throws Exception {
        Long annualId = periodId(TermType.ANUAL);

        mockMvc.perform(put("/v1/academic-periods/{id}", annualId)
                        .contentType("application/json")
                        .content("""
                                {"endDate":"%d-11-30","recessStart":"%d-07-06","recessEnd":"%d-07-27"}
                                """.formatted(year, year, year)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.recessStart").value(year + "-07-06"))
                .andExpect(jsonPath("$.recessEnd").value(year + "-07-27"));

        AcademicPeriod firstTerm = academicPeriodRepository
                .findByYearAndSemester(year, TermType.PRIMER_CUATRIMESTRE.getSemester()).orElseThrow();
        AcademicPeriod secondTerm = academicPeriodRepository
                .findByYearAndSemester(year, TermType.SEGUNDO_CUATRIMESTRE.getSemester()).orElseThrow();
        assertThat(firstTerm.getEndDate()).isEqualTo(LocalDate.of(year, 7, 5));
        assertThat(secondTerm.getStartDate()).isEqualTo(LocalDate.of(year, 7, 28));
    }

    @Test
    @DisplayName("AUXILIAR_AULICO no puede modificar: 403")
    void auxiliarForbidden() throws Exception {
        auxiliarMockMvc.perform(put("/v1/academic-periods/{id}", periodId(TermType.ANUAL))
                        .contentType("application/json")
                        .content("{\"endDate\":\"%d-11-30\"}".formatted(year)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("receso en un período no ANUAL: 422")
    void recessOnNonAnnual() throws Exception {
        mockMvc.perform(put("/v1/academic-periods/{id}", periodId(TermType.PRIMER_CUATRIMESTRE))
                        .contentType("application/json")
                        .content("""
                                {"endDate":"%d-07-31","recessStart":"%d-07-06","recessEnd":"%d-07-27"}
                                """.formatted(year, year, year)))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    @DisplayName("período inexistente: 404")
    void missingPeriod() throws Exception {
        mockMvc.perform(put("/v1/academic-periods/{id}", 999_999_999L)
                        .contentType("application/json")
                        .content("{\"endDate\":\"%d-11-30\"}".formatted(year)))
                .andExpect(status().isNotFound());
    }
}
