package ar.edu.utn.frc.siga.cameracount.controller;

import ar.edu.utn.frc.siga.cameracount.dto.request.CameraCountRequestDto;
import ar.edu.utn.frc.siga.cameracount.dto.response.CameraCountHealthResponseDto;
import ar.edu.utn.frc.siga.cameracount.dto.response.CameraCountResponseDto;
import ar.edu.utn.frc.siga.cameracount.exception.CameraCountFailedException;
import ar.edu.utn.frc.siga.cameracount.exception.CameraCountUnavailableException;
import ar.edu.utn.frc.siga.cameracount.exception.InvalidCameraUrlException;
import ar.edu.utn.frc.siga.cameracount.model.CameraCountStatus;
import ar.edu.utn.frc.siga.cameracount.service.CameraCountService;
import ar.edu.utn.frc.siga.common.exception.GlobalExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Named.named;
import static org.junit.jupiter.params.provider.Arguments.arguments;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
@DisplayName("CameraCountController: POST /v1/camera-counts y GET /v1/camera-counts/health")
class CameraCountControllerTest {

    private static final String COUNTS = "/v1/camera-counts";
    private static final String HEALTH = "/v1/camera-counts/health";
    private static final String RTSP = "rtsp://admin:s3cr3t@172.16.1.10:554/Streaming/Channels/101";

    @Mock
    private CameraCountService service;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(new CameraCountController(service))
                .setControllerAdvice(new GlobalExceptionHandler())
                .addPlaceholderValue("siga.api.base-path", "/v1")
                .build();
    }

    private ResultActions postCount(String body) throws Exception {
        return mockMvc.perform(post(COUNTS).contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private static String body(String rtspUrl, Integer samples) {
        return "{\"rtspUrl\":\"" + rtspUrl + "\"" + (samples == null ? "" : ",\"samples\":" + samples) + "}";
    }

    private static void assertNoCameraUrlIn(ResultActions result) throws Exception {
        assertThat(result.andReturn().getResponse().getContentAsString())
                .doesNotContain("s3cr3t", "admin", "172.16.1.10");
    }

    @Test
    @DisplayName("POST válido: 200 con todos los campos, durationMs en milisegundos, y el service recibe la URL y samples")
    void validPostReturnsMappedCount() throws Exception {
        when(service.count(RTSP, 5)).thenReturn(new CameraCountResponseDto(CameraCountStatus.OK, 3, 3.4086, 5, 4,
                Instant.parse("2026-10-24T03:43:43Z"), 24117L));

        ResultActions result = postCount(body(RTSP, 5))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("OK"))
                .andExpect(jsonPath("$.people").value(3))
                .andExpect(jsonPath("$.estimate").value(3.4086))
                .andExpect(jsonPath("$.samplesRequested").value(5))
                .andExpect(jsonPath("$.samplesOk").value(4))
                .andExpect(jsonPath("$.measuredAt").exists())
                .andExpect(jsonPath("$.durationMs").value(24117));

        verify(service).count(RTSP, 5);
        assertNoCameraUrlIn(result);
    }

    @Test
    @DisplayName("POST sin samples: el service recibe null")
    void missingSamplesReachesServiceAsNull() throws Exception {
        when(service.count(RTSP, null)).thenReturn(new CameraCountResponseDto(CameraCountStatus.OK, 1, 1.0, 5, 5,
                Instant.parse("2026-10-24T03:43:43Z"), 2000L));

        postCount(body(RTSP, null))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.durationMs").value(2000));

        verify(service).count(RTSP, null);
    }

    @Test
    @DisplayName("POST con NO_SIGNAL: 200 con status NO_SIGNAL y sin people ni estimate")
    void noSignalIsAValidResponse() throws Exception {
        when(service.count(RTSP, 3)).thenReturn(new CameraCountResponseDto(CameraCountStatus.NO_SIGNAL, null, null, 3, 0,
                Instant.parse("2026-10-24T03:43:43Z"), 1200L));

        postCount(body(RTSP, 3))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("NO_SIGNAL"))
                .andExpect(jsonPath("$.people").doesNotExist())
                .andExpect(jsonPath("$.estimate").doesNotExist())
                .andExpect(jsonPath("$.samplesOk").value(0));
    }

    @ParameterizedTest(name = "rtspUrl={0}")
    @ValueSource(strings = {
            "RTSP://admin:s3cr3t@172.16.1.10:554/x",
            "rtsps://admin:s3cr3t@172.16.1.10:554/x",
            "RtSp://a"
    })
    @DisplayName("rtsp:// y rtsps:// en cualquier combinación de mayúsculas pasan y se mandan al service sin cambios")
    void rtspSchemeIsCaseInsensitive(String url) throws Exception {
        when(service.count(url, null)).thenReturn(new CameraCountResponseDto(CameraCountStatus.OK, 1, 1.0, 5, 5,
                Instant.parse("2026-10-24T03:43:43Z"), 10L));

        postCount(body(url, null)).andExpect(status().isOk());

        verify(service).count(url, null);
    }

    @Test
    @DisplayName("rtspUrl de 2048 caracteres (el máximo) pasa")
    void urlAtMaximumLengthIsAccepted() throws Exception {
        String url = "rtsp://" + "a".repeat(2041);
        when(service.count(url, null)).thenReturn(new CameraCountResponseDto(CameraCountStatus.OK, 1, 1.0, 5, 5,
                Instant.parse("2026-10-24T03:43:43Z"), 10L));

        postCount(body(url, null)).andExpect(status().isOk());

        verify(service).count(url, null);
    }

    @ParameterizedTest(name = "samples={0} es válido")
    @ValueSource(ints = {1, 10})
    @DisplayName("samples 1 y 10 (extremos del rango) pasan al service")
    void samplesAtRangeBoundariesAreAccepted(int samples) throws Exception {
        when(service.count(RTSP, samples)).thenReturn(new CameraCountResponseDto(CameraCountStatus.OK, 1, 1.0, samples, samples,
                Instant.parse("2026-10-24T03:43:43Z"), 10L));

        postCount(body(RTSP, samples)).andExpect(status().isOk());

        verify(service).count(RTSP, samples);
    }

    static Stream<Arguments> invalidBodies() {
        return Stream.of(
                arguments(named("rtspUrl ausente", "{\"samples\":5}"), "rtspUrl"),
                arguments(named("rtspUrl null", "{\"rtspUrl\":null}"), "rtspUrl"),
                arguments(named("rtspUrl vacía", body("", null)), "rtspUrl"),
                arguments(named("rtspUrl en blanco", body("   ", null)), "rtspUrl"),
                arguments(named("http con credenciales", body("http://admin:s3cr3t@172.16.1.10/x", null)), "rtspUrl"),
                arguments(named("sin host (rtsp://)", body("rtsp://", null)), "rtspUrl"),
                arguments(named("scheme pegado a un prefijo", body("xrtsp://admin:s3cr3t@172.16.1.10/x", null)), "rtspUrl"),
                arguments(named("2049 caracteres", body("rtsp://" + "a".repeat(2042), null)), "rtspUrl"),
                arguments(named("samples 0", body(RTSP, 0)), "samples"),
                arguments(named("samples 11", body(RTSP, 11)), "samples"),
                arguments(named("samples negativo", body(RTSP, -1)), "samples")
        );
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidBodies")
    @DisplayName("body inválido: 400 con el error del campo, sin llamar al service y sin devolver la URL")
    void invalidBodyIsRejectedWithoutCallingService(String requestBody, String field) throws Exception {
        ResultActions result = postCount(requestBody)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors." + field).isNotEmpty());

        verifyNoInteractions(service);
        assertNoCameraUrlIn(result);
    }

    @ParameterizedTest(name = "body={0}")
    @ValueSource(strings = {"{", "no es json", ""})
    @DisplayName("body ilegible o vacío: 400 sin llamar al service")
    void unreadableBodyIsRejectedWithoutCallingService(String requestBody) throws Exception {
        postCount(requestBody).andExpect(status().isBadRequest());

        verifyNoInteractions(service);
    }

    @Test
    @DisplayName("Unavailable con Retry-After del service: 503 y el header Retry-After=10 en la respuesta")
    void unavailableWithRetryAfterPropagatesHeader() throws Exception {
        when(service.count(RTSP, 5)).thenThrow(CameraCountUnavailableException.retryAfter(
                "El servicio de conteo por cámaras está saturado o iniciándose", "10"));

        ResultActions result = postCount(body(RTSP, 5))
                .andExpect(status().isServiceUnavailable())
                .andExpect(header().string("Retry-After", "10"))
                .andExpect(jsonPath("$.status").value(503));

        assertNoCameraUrlIn(result);
    }

    @Test
    @DisplayName("Unavailable sin Retry-After: 503 sin ese header")
    void unavailableWithoutRetryAfterHasNoHeader() throws Exception {
        when(service.count(RTSP, 5)).thenThrow(new CameraCountUnavailableException(
                "El conteo por cámaras está deshabilitado en este entorno"));

        postCount(body(RTSP, 5))
                .andExpect(status().isServiceUnavailable())
                .andExpect(header().doesNotExist("Retry-After"))
                .andExpect(jsonPath("$.detail").value("El conteo por cámaras está deshabilitado en este entorno"));
    }

    @Test
    @DisplayName("InvalidCameraUrlException del service: 422 sin la URL en el body")
    void invalidCameraUrlBecomesUnprocessable() throws Exception {
        when(service.count(RTSP, 5)).thenThrow(
                new InvalidCameraUrlException("El servicio de conteo por cámaras rechazó la URL de la cámara"));

        ResultActions result = postCount(body(RTSP, 5))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.status").value(422));

        assertNoCameraUrlIn(result);
    }

    @Test
    @DisplayName("CameraCountFailedException del service: 502 sin la URL en el body")
    void failedCountBecomesBadGateway() throws Exception {
        when(service.count(RTSP, 5)).thenThrow(
                new CameraCountFailedException("El servicio de conteo por cámaras respondió con error 500"));

        ResultActions result = postCount(body(RTSP, 5))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.status").value(502));

        assertNoCameraUrlIn(result);
    }

    @Test
    @DisplayName("GET /health con el modelo listo: 200 con ready, estimator y version")
    void healthReturnsReadyService() throws Exception {
        when(service.health()).thenReturn(new CameraCountHealthResponseDto(true, "yolo", "0.1.0"));

        mockMvc.perform(get(HEALTH))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ready").value(true))
                .andExpect(jsonPath("$.estimator").value("yolo"))
                .andExpect(jsonPath("$.version").value("0.1.0"));
    }

    @Test
    @DisplayName("GET /health con el modelo cargando: 200 con ready=false")
    void healthReturnsNotReadyWhileLoadingModel() throws Exception {
        when(service.health()).thenReturn(new CameraCountHealthResponseDto(false, null, "0.1.0"));

        mockMvc.perform(get(HEALTH))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ready").value(false))
                .andExpect(jsonPath("$.version").value("0.1.0"));
    }

    @Test
    @DisplayName("GET /health con el servicio caído: 503")
    void healthPropagatesUnavailable() throws Exception {
        when(service.health()).thenThrow(new CameraCountUnavailableException("No se pudo comunicar"));

        mockMvc.perform(get(HEALTH)).andExpect(status().isServiceUnavailable());
    }

    @Test
    @DisplayName("CameraCountRequestDto.toString enmascara la URL y no incluye usuario ni clave")
    void requestDtoToStringMasksUrl() {
        String text = new CameraCountRequestDto(RTSP, 5).toString();

        assertThat(text).doesNotContain("s3cr3t", "admin").contains("***@172.16.1.10:554");
    }

    @Test
    @DisplayName("CameraCountRequestDto.toString con URL null no falla")
    void requestDtoToStringToleratesNullUrl() {
        assertThat(new CameraCountRequestDto(null, null).toString()).contains("***");
    }
}
