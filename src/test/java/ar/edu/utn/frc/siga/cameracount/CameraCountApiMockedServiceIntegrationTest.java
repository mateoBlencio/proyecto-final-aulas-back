package ar.edu.utn.frc.siga.cameracount;

import ar.edu.utn.frc.siga.AbstractIntegrationTest;
import ar.edu.utn.frc.siga.cameracount.dto.response.CameraCountHealthResponseDto;
import ar.edu.utn.frc.siga.cameracount.dto.response.CameraCountResponseDto;
import ar.edu.utn.frc.siga.cameracount.exception.CameraCountUnavailableException;
import ar.edu.utn.frc.siga.cameracount.model.CameraCountStatus;
import ar.edu.utn.frc.siga.cameracount.service.CameraCountService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.time.Instant;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("Conteo por cámaras API (integración, CameraCountService mockeado)")
class CameraCountApiMockedServiceIntegrationTest extends AbstractIntegrationTest {

    private static final String COUNTS = "/v1/camera-counts";
    private static final String HEALTH = "/v1/camera-counts/health";
    private static final String RTSP = "rtsp://admin:s3cr3t@172.16.1.10:554/Streaming/Channels/101";

    @MockitoBean
    private CameraCountService service;

    private static MockHttpServletRequestBuilder postCount(String body) {
        return post(COUNTS).contentType(MediaType.APPLICATION_JSON).content(body);
    }

    @Test
    @DisplayName("POST con el service mockeado: el JSON real trae measuredAt en ISO-8601 y durationMs en milisegundos")
    void realJsonHasIsoInstantAndDurationInMillis() throws Exception {
        when(service.count(RTSP, 3)).thenReturn(new CameraCountResponseDto(CameraCountStatus.OK, 3, 3.4086, 3, 3,
                Instant.parse("2026-10-24T03:43:43Z"), 24117L));

        mockMvc.perform(postCount("{\"rtspUrl\":\"" + RTSP + "\",\"samples\":3}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("OK"))
                .andExpect(jsonPath("$.people").value(3))
                .andExpect(jsonPath("$.estimate").value(3.4086))
                .andExpect(jsonPath("$.samplesRequested").value(3))
                .andExpect(jsonPath("$.samplesOk").value(3))
                .andExpect(jsonPath("$.measuredAt").value("2026-10-24T03:43:43Z"))
                .andExpect(jsonPath("$.durationMs").value(24117));

        verify(service).count(RTSP, 3);
    }

    @Test
    @DisplayName("POST con Unavailable con Retry-After: el header sale por la cadena real de filtros")
    void retryAfterHeaderSurvivesTheFilterChain() throws Exception {
        when(service.count(RTSP, null)).thenThrow(CameraCountUnavailableException.retryAfter(
                "El servicio de conteo por cámaras está saturado o iniciándose", "10"));

        mockMvc.perform(postCount("{\"rtspUrl\":\"" + RTSP + "\"}"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(header().string("Retry-After", "10"));
    }

    @Test
    @DisplayName("POST inválido: 400 por la validación del contexto real y el service no se toca")
    void invalidBodyNeverReachesTheService() throws Exception {
        mockMvc.perform(postCount("{\"rtspUrl\":\"" + RTSP + "\",\"samples\":0}"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(service);
    }

    @Test
    @DisplayName("GET /health con el service mockeado: 200 con ready, estimator y version")
    void healthReturnsServiceState() throws Exception {
        when(service.health()).thenReturn(new CameraCountHealthResponseDto(true, "yolo", "0.1.0"));

        mockMvc.perform(get(HEALTH))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ready").value(true))
                .andExpect(jsonPath("$.estimator").value("yolo"))
                .andExpect(jsonPath("$.version").value("0.1.0"));
    }
}
