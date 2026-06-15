package PF.classroom_allocation.space.controller;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.RequestEntity;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.web.client.DefaultResponseErrorHandler;
import org.springframework.web.client.RestTemplate;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Sql(scripts = "/space/integration/setup-classroom.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
@Sql(scripts = "/space/integration/cleanup.sql", executionPhase = Sql.ExecutionPhase.AFTER_TEST_METHOD)
class ClassroomControllerIntegrationTest {

    private static final String PATH = "/api/v1/classrooms";

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
        baseUrl = "http://localhost:" + port + PATH;
    }

    // ─── HELPERS ──────────────────────────────────────────────────────────────

    private Map<String, Object> parseMap(ResponseEntity<String> response) throws Exception {
        return objectMapper.readValue(response.getBody(), new TypeReference<>() {});
    }

    private ResponseEntity<String> postJson(String url, String body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<String> request = new HttpEntity<>(body, headers);
        return restTemplate.exchange(url, HttpMethod.POST, request, String.class);
    }

    private ResponseEntity<String> putJson(String url, String body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<String> request = new HttpEntity<>(body, headers);
        return restTemplate.exchange(url, HttpMethod.PUT, request, String.class);
    }

    // ─── CREATE ───────────────────────────────────────────────────────────────

    @Test
    void create_shouldReturn201AndPersist() throws Exception {
        String body = """
                {"roomNumber":"200","capacity":40,"floor":3,"classroomTypeId":1,"available":true,"buildingId":1}
                """;

        ResponseEntity<String> createResponse = postJson(baseUrl, body);
        assertThat(createResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        Map<String, Object> created = parseMap(createResponse);
        assertThat(created.get("roomNumber")).isEqualTo("200");
        assertThat(created.get("capacity")).isEqualTo(40);
        assertThat(created.get("floor")).isEqualTo(3);
        assertThat(created.get("available")).isEqualTo(true);
        assertThat(created.get("buildingId")).isEqualTo(1);
        assertThat(created.get("classroomTypeId")).isEqualTo(1);

        Integer id = (Integer) created.get("id");
        assertThat(id).isNotNull();

        ResponseEntity<String> getResponse = restTemplate.getForEntity(baseUrl + "/" + id, String.class);
        assertThat(getResponse.getStatusCode()).isEqualTo(HttpStatus.OK);

        Map<String, Object> fetched = parseMap(getResponse);
        assertThat(fetched.get("id")).isEqualTo(id);
        assertThat(fetched.get("roomNumber")).isEqualTo("200");
    }

    @Test
    void create_shouldReturn400WhenRoomNumberBlank() {
        String body = """
                {"roomNumber":"","capacity":30,"floor":2,"classroomTypeId":1,"available":true,"buildingId":1}
                """;

        ResponseEntity<String> response = postJson(baseUrl, body);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void create_shouldReturn400WhenDuplicateRoomNumber() throws Exception {
        String body = """
                {"roomNumber":"101","capacity":30,"floor":2,"classroomTypeId":1,"available":true,"buildingId":1}
                """;

        ResponseEntity<String> response = postJson(baseUrl, body);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

        Map<String, Object> parsed = parseMap(response);
        assertThat((String) parsed.get("error")).contains("already exists");
    }

    @Test
    void create_shouldReturn400WhenFloorExceedsBuilding() throws Exception {
        String body = """
                {"roomNumber":"300","capacity":30,"floor":6,"classroomTypeId":1,"available":true,"buildingId":1}
                """;

        ResponseEntity<String> response = postJson(baseUrl, body);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

        Map<String, Object> parsed = parseMap(response);
        assertThat((String) parsed.get("error")).contains("exceeds");
    }

    // ─── FIND BY ID ───────────────────────────────────────────────────────────

    @Test
    void findById_shouldReturn200() throws Exception {
        ResponseEntity<String> response = restTemplate.getForEntity(baseUrl + "/1", String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);

        Map<String, Object> parsed = parseMap(response);
        assertThat(parsed.get("id")).isEqualTo(1);
        assertThat(parsed.get("roomNumber")).isEqualTo("101");
        assertThat(parsed.get("capacity")).isEqualTo(30);
        assertThat(parsed.get("floor")).isEqualTo(2);
        assertThat(parsed.get("available")).isEqualTo(true);
        assertThat(parsed.get("buildingId")).isEqualTo(1);
        assertThat(parsed.get("buildingName")).isEqualTo("Pabellon A");
        assertThat(parsed.get("classroomTypeId")).isEqualTo(1);
        assertThat(parsed.get("classroomTypeDescription")).isEqualTo("CLASSROOM");
    }

    @Test
    void findById_shouldReturn404() {
        ResponseEntity<String> response = restTemplate.getForEntity(baseUrl + "/999", String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    // ─── FIND ALL ─────────────────────────────────────────────────────────────

    @Test
    void findAll_shouldReturnPaginatedResults() throws Exception {
        ResponseEntity<String> response = restTemplate.getForEntity(
                baseUrl + "?page=0&size=10&buildingId=1", String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);

        Map<String, Object> parsed = parseMap(response);
        assertThat(parsed.get("totalElements")).isEqualTo(2);
        assertThat(parsed.get("totalPages")).isEqualTo(1);
    }

    @Test
    void findAll_shouldReturnEmptyPageWhenNoMatch() throws Exception {
        ResponseEntity<String> response = restTemplate.getForEntity(
                baseUrl + "?capacityMin=100", String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);

        Map<String, Object> parsed = parseMap(response);
        assertThat(parsed.get("totalElements")).isEqualTo(0);
    }

    // ─── UPDATE ───────────────────────────────────────────────────────────────

    @Test
    void update_shouldReturn200AndUpdateFields() throws Exception {
        String body = """
                {"roomNumber":"101","capacity":50,"floor":3,"classroomTypeId":2,"available":false,"buildingId":2}
                """;

        ResponseEntity<String> updateResponse = putJson(baseUrl + "/1", body);
        assertThat(updateResponse.getStatusCode()).isEqualTo(HttpStatus.OK);

        ResponseEntity<String> getResponse = restTemplate.getForEntity(baseUrl + "/1", String.class);
        assertThat(getResponse.getStatusCode()).isEqualTo(HttpStatus.OK);

        Map<String, Object> fetched = parseMap(getResponse);
        assertThat(fetched.get("capacity")).isEqualTo(50);
        assertThat(fetched.get("floor")).isEqualTo(3);
        assertThat(fetched.get("available")).isEqualTo(false);
        assertThat(fetched.get("buildingId")).isEqualTo(2);
        assertThat(fetched.get("buildingName")).isEqualTo("Pabellon B");
        assertThat(fetched.get("classroomTypeId")).isEqualTo(2);
        assertThat(fetched.get("classroomTypeDescription")).isEqualTo("LABORATORIO");
    }

    @Test
    void update_shouldReturn404WhenNotFound() {
        String body = """
                {"roomNumber":"999","capacity":30,"floor":2,"classroomTypeId":1,"available":true,"buildingId":1}
                """;

        ResponseEntity<String> response = putJson(baseUrl + "/999", body);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    // ─── DELETE ───────────────────────────────────────────────────────────────

    @Test
    void delete_shouldReturn204AndSoftDelete() throws Exception {
        ResponseEntity<String> deleteResponse = restTemplate.exchange(
                RequestEntity.delete(baseUrl + "/1").build(), String.class);
        assertThat(deleteResponse.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        ResponseEntity<String> getResponse = restTemplate.getForEntity(baseUrl + "/1", String.class);
        assertThat(getResponse.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void delete_shouldReturn404WhenNotFound() {
        ResponseEntity<String> response = restTemplate.exchange(
                RequestEntity.delete(baseUrl + "/999").build(), String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }
}
