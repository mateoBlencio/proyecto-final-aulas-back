package ar.edu.utn.frc.siga.support;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.RequestEntity;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

import java.net.URI;

public final class IntegrationAuthTestSupport {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private IntegrationAuthTestSupport() {
    }

    public static String obtainToken(int port, String email, String password) {
        RestTemplate restTemplate = new RestTemplate();
        String body = """
                {"email":"%s","password":"%s"}
                """.formatted(email, password);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        RequestEntity<String> request = RequestEntity
                .post(URI.create("http://localhost:" + port + "/api/auth/login"))
                .headers(headers)
                .body(body);

        ResponseEntity<String> response = restTemplate.exchange(request, String.class);
        try {
            JsonNode json = OBJECT_MAPPER.readTree(response.getBody());
            return json.get("accessToken").asText();
        } catch (Exception e) {
            throw new IllegalStateException("No se pudo obtener el token para " + email
                    + ", status=" + response.getStatusCode() + ", body=" + response.getBody(), e);
        }
    }
}
