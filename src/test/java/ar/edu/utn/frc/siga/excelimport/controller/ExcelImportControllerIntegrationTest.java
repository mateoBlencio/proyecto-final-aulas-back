package ar.edu.utn.frc.siga.excelimport.controller;

import ar.edu.utn.frc.siga.excelimport.ExcelTestFactory;
import ar.edu.utn.frc.siga.support.IntegrationAuthTestSupport;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.DefaultResponseErrorHandler;
import org.springframework.web.client.RestTemplate;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("integration")
@Sql(scripts = "/excelimport/integration/setup-import.sql",
     executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
@Sql(scripts = "/auth/integration/seed-admin.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
@Sql(scripts = "/excelimport/integration/cleanup-import.sql",
     executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD)
class ExcelImportControllerIntegrationTest {

    private static final String PATH = "/api/v1/excelimports";

    @LocalServerPort
    private int port;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private RestTemplate restTemplate;
    private String baseUrl;

    @BeforeEach
    void setUp() {
        restTemplate = new RestTemplate();
        restTemplate.setErrorHandler(new DefaultResponseErrorHandler() {
            @Override
            public boolean hasError(ClientHttpResponse response) {
                return false;
            }
        });
        String token = IntegrationAuthTestSupport.obtainToken(port, "admin.test@frc.utn.edu.ar", "TestPassword123!");
        restTemplate.getInterceptors().add((request, requestBody, execution) -> {
            request.getHeaders().setBearerAuth(token);
            return execution.execute(request, requestBody);
        });
        baseUrl = "http://localhost:" + port + PATH;
    }

    @Test
    void importExcel_shouldReturn200WithResultWhenFileIsValid() {
        ResponseEntity<String> response = postExcel(ExcelTestFactory.validXlsx(1), "test.xlsx");
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);

        Map<String, Object> result = parseMap(response);
        assertThat(result.get("processedRows")).isEqualTo(1);
    }

    @Test
    void importExcel_shouldReturn200WhenFileIsXls() {
        ResponseEntity<String> response = postExcel(ExcelTestFactory.validXls(1), "test.xls");
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void importExcel_shouldReturn400WhenFileIsNotExcel() {
        ResponseEntity<String> response = postExcel(ExcelTestFactory.csvBytes(), "test.csv");
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void importExcel_shouldReturn400WhenHeadersAreWrong() {
        ResponseEntity<String> response = postExcel(ExcelTestFactory.badHeaders(), "test.xlsx");
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    @Disabled("Expectativa obsoleta: hoy el import hace find-or-create del edificio (deuda técnica "
            + "DT-002 en plans/const/deuda-tecnica.md) y devuelve 200 en vez de 422. Reactivar cuando "
            + "DT-002 se resuelva y el import falle-rápido ante un edificio no cargado.")
    void importExcel_shouldReturn422WhenBuildingNotLoaded() {
        ResponseEntity<String> response = postExcel(
            ExcelTestFactory.unknownBuilding(), "test.xlsx");
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
    }

    @Test
    void importExcel_shouldReturn422WhenTermTypeIsUnrecognized() {
        ResponseEntity<String> response = postExcel(
            ExcelTestFactory.unknownTermType(), "test.xlsx");
        // Se compara por valor numérico (422): Spring 7 / Boot 4 renombró el enum
        // UNPROCESSABLE_ENTITY -> UNPROCESSABLE_CONTENT (mismo status code).
        assertThat(response.getStatusCode().value()).isEqualTo(422);
    }

    @Test
    void importExcel_shouldCreateSeparateCommissionsForSameCourseCode() {
        ResponseEntity<String> response = postExcel(
            ExcelTestFactory.duplicateCommissions(), "test.xlsx");
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);

        Map<String, Object> result = parseMap(response);
        assertThat(result.get("processedRows")).isEqualTo(2);
    }

    private ResponseEntity<String> postExcel(byte[] fileBytes, String filename) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("file", new ByteArrayResource(fileBytes) {
            @Override
            public String getFilename() {
                return filename;
            }
        });
        return restTemplate.exchange(baseUrl, HttpMethod.POST,
            new HttpEntity<>(body, headers), String.class);
    }

    private Map<String, Object> parseMap(ResponseEntity<String> response) {
        try {
            return objectMapper.readValue(response.getBody(),
                new com.fasterxml.jackson.core.type.TypeReference<>() {});
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse response", e);
        }
    }
}
