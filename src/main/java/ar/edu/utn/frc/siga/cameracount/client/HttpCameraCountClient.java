package ar.edu.utn.frc.siga.cameracount.client;

import ar.edu.utn.frc.siga.cameracount.client.dto.CameraRequest;
import ar.edu.utn.frc.siga.cameracount.client.dto.RawCount;
import ar.edu.utn.frc.siga.cameracount.client.dto.RawHealth;
import ar.edu.utn.frc.siga.cameracount.client.dto.RawProbe;
import ar.edu.utn.frc.siga.cameracount.dto.response.CameraCountHealthResponseDto;
import ar.edu.utn.frc.siga.cameracount.dto.response.CameraCountResponseDto;
import ar.edu.utn.frc.siga.cameracount.dto.response.CameraProbeResponseDto;
import ar.edu.utn.frc.siga.cameracount.exception.CameraCountFailedException;
import ar.edu.utn.frc.siga.cameracount.exception.CameraCountUnavailableException;
import ar.edu.utn.frc.siga.cameracount.exception.InvalidCameraUrlException;
import ar.edu.utn.frc.siga.cameracount.model.CameraCountStatus;
import ar.edu.utn.frc.siga.cameracount.model.CameraProbeFailure;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

@Slf4j
public class HttpCameraCountClient {

    private static final String COUNT_PATH = "/conteo";
    private static final String PROBE_PATH = "/probar";
    private static final String HEALTH_PATH = "/health";

    private static final String UNEXPECTED_RESPONSE = "El servicio de conteo por cámaras devolvió una respuesta inesperada";

    private final RestClient restClient;

    public HttpCameraCountClient(RestClient restClient) {
        this.restClient = restClient;
    }

    public CameraCountResponseDto count(String rtspUrl, Integer samples) {
        return toCount(post(COUNT_PATH, new CameraRequest(rtspUrl, samples), RawCount.class));
    }

    public CameraProbeResponseDto probe(String rtspUrl) {
        return toProbe(post(PROBE_PATH, new CameraRequest(rtspUrl, null), RawProbe.class));
    }

    public CameraCountHealthResponseDto health() {
        RawHealth raw;
        try {
            // El servicio responde 503 con el body normal mientras carga el modelo: eso es "no listo", no un error.
            raw = restClient.get().uri(HEALTH_PATH).exchange((request, response) -> {
                int status = response.getStatusCode().value();
                if (status != 200 && status != 503) {
                    throw translate(status, response.getHeaders(), HEALTH_PATH);
                }
                return response.bodyTo(RawHealth.class);
            });
        } catch (ResourceAccessException e) {
            throw unreachable(HEALTH_PATH, e);
        } catch (RestClientException e) {
            throw new CameraCountFailedException(UNEXPECTED_RESPONSE, e);
        }
        if (raw == null) {
            throw new CameraCountFailedException(UNEXPECTED_RESPONSE);
        }
        return toHealth(raw);
    }

    private <T> T post(String path, CameraRequest request, Class<T> responseType) {
        T body;
        try {
            body = restClient.post()
                    .uri(path)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .body(responseType);
        } catch (RestClientResponseException e) {
            throw translate(e.getStatusCode().value(), e.getResponseHeaders(), RtspUrls.mask(request.rtspUrl()));
        } catch (ResourceAccessException e) {
            throw unreachable(RtspUrls.mask(request.rtspUrl()), e);
        } catch (RestClientException e) {
            throw new CameraCountFailedException(UNEXPECTED_RESPONSE, e);
        }
        if (body == null) {
            throw new CameraCountFailedException(UNEXPECTED_RESPONSE);
        }
        return body;
    }

    private static CameraCountUnavailableException unreachable(String target, ResourceAccessException e) {
        log.warn("No se pudo comunicar con el servicio de conteo ({}): {}", target, e.getMessage());
        return new CameraCountUnavailableException("No se pudo comunicar con el servicio de conteo por cámaras", e);
    }

    private static RuntimeException translate(int status, HttpHeaders headers, String target) {
        return switch (status) {
            case 503 -> {
                log.warn("El servicio de conteo está saturado o cargando el modelo ({})", target);
                String detail = "El servicio de conteo por cámaras está saturado o iniciándose";
                String retryAfter = headers == null ? null : headers.getFirst(HttpHeaders.RETRY_AFTER);
                yield retryAfter == null
                        ? new CameraCountUnavailableException(detail)
                        : CameraCountUnavailableException.retryAfter(detail, retryAfter);
            }
            case 422 -> {
                log.warn("El servicio de conteo rechazó la URL de la cámara ({})", target);
                yield new InvalidCameraUrlException("El servicio de conteo por cámaras rechazó la URL de la cámara");
            }
            case 401 -> {
                log.error("El servicio de conteo rechazó la API key (401): revisar siga.camera-count.api-key");
                yield new CameraCountFailedException("El servicio de conteo por cámaras rechazó las credenciales del backend");
            }
            default -> {
                log.warn("El servicio de conteo respondió {} ({})", status, target);
                yield new CameraCountFailedException("El servicio de conteo por cámaras respondió con error " + status);
            }
        };
    }

    private static CameraCountResponseDto toCount(RawCount raw) {
        return new CameraCountResponseDto(countStatus(raw.estado()), raw.cantidad(), raw.estimado(),
                raw.muestrasPedidas(), raw.muestrasOk(), raw.medidoEn(), raw.duracionMs());
    }

    private static CameraProbeResponseDto toProbe(RawProbe raw) {
        return new CameraProbeResponseDto(raw.conecta(), raw.ancho(), raw.alto(), probeFailure(raw.motivo()),
                raw.duracionMs());
    }

    private static CameraCountHealthResponseDto toHealth(RawHealth raw) {
        boolean ready = switch (String.valueOf(raw.estado())) {
            case "ok" -> true;
            case "cargando" -> false;
            default -> throw unknownValue("estado", raw.estado());
        };
        return new CameraCountHealthResponseDto(ready, raw.estimador(), raw.version());
    }

    private static CameraCountStatus countStatus(String estado) {
        return switch (String.valueOf(estado)) {
            case "ok" -> CameraCountStatus.OK;
            case "sin_senal" -> CameraCountStatus.NO_SIGNAL;
            default -> throw unknownValue("estado", estado);
        };
    }

    private static CameraProbeFailure probeFailure(String motivo) {
        if (motivo == null) {
            return null;
        }
        return switch (motivo) {
            case "no_conecta" -> CameraProbeFailure.NOT_CONNECTING;
            case "sin_fotograma" -> CameraProbeFailure.NO_FRAME;
            case "timeout" -> CameraProbeFailure.TIMEOUT;
            default -> throw unknownValue("motivo", motivo);
        };
    }

    private static CameraCountFailedException unknownValue(String field, String value) {
        log.warn("El servicio de conteo devolvió un {} desconocido: {}", field, value);
        return new CameraCountFailedException(UNEXPECTED_RESPONSE);
    }
}
