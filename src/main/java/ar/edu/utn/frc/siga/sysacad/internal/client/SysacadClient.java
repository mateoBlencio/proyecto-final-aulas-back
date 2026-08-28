package ar.edu.utn.frc.siga.sysacad.internal.client;

import ar.edu.utn.frc.siga.sysacad.internal.config.SysacadProperties;
import ar.edu.utn.frc.siga.sysacad.internal.exception.SysacadUnavailableException;

import ar.edu.utn.frc.siga.sysacad.internal.client.dto.ViewResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.util.UriBuilder;

import java.net.URI;
import java.time.Duration;
import java.util.List;

@Slf4j
@Component
@ConditionalOnProperty(prefix = "siga.sysacad", name = "enabled", havingValue = "true")
public class SysacadClient {

    private static final String VIEW_PATH = "/api/data/views/{view}";
    private static final Duration BASE_BACKOFF = Duration.ofSeconds(1);
    private static final Duration MAX_BACKOFF = Duration.ofSeconds(30);

    private final RestClient restClient;
    private final int maxRetries;

    public SysacadClient(RestClient sysacadRestClient, SysacadProperties properties) {
        this.restClient = sysacadRestClient;
        this.maxRetries = properties.getMaxRetries();
    }

    public <T> List<T> fetchRows(String view, ViewQuery query,
                                 ParameterizedTypeReference<ViewResponse<T>> responseType) {
        for (int attempt = 0; ; attempt++) {
            try {
                ViewResponse<T> response = restClient.get()
                        .uri(uriBuilder -> buildUri(uriBuilder, view, query))
                        .retrieve()
                        .body(responseType);
                return response == null || response.rows() == null ? List.of() : response.rows();
            } catch (RestClientResponseException e) {
                if (attempt >= maxRetries || !isRetryable(e.getStatusCode())) {
                    throw e;
                }
                Duration backoff = backoffFor(e.getResponseHeaders(), attempt);
                log.warn("SysAcad respondió {} en la vista {}; reintento {}/{} en {} ms",
                        e.getStatusCode().value(), view, attempt + 1, maxRetries, backoff.toMillis());
                sleep(backoff);
            } catch (ResourceAccessException e) {
                throw new SysacadUnavailableException(
                        "No se pudo conectar con SysAcad para la vista " + view, e);
            }
        }
    }

    /**
     * TEMPORAL — workaround al tope {@code maxRows} por vista de SysAcad (200 en la mayoría) hasta tener
     * paginación real. Trae el tope en orden ascendente y descendente por {@code sortColumn} y une ambas
     * deduplicando (por {@code equals} del record de fila), consiguiendo ~2x cobertura: los dos extremos
     * del orden. NO garantiza traer todo si la vista tiene más de {@code 2*maxRows} filas — un hueco en el
     * medio sigue perdiéndose. Quitar y reemplazar por paginación cuando exista (pendiente, junto con la
     * vista real de Materias). No usar como solución definitiva.
     */
    public <T> List<T> fetchRowsSpanning(String view, String sortColumn, Integer limit,
                                         ParameterizedTypeReference<ViewResponse<T>> responseType) {
        List<T> ascending = fetchRows(view, ViewQuery.ascendingBy(sortColumn, limit), responseType);
        List<T> descending = fetchRows(view, ViewQuery.descendingBy(sortColumn, limit), responseType);
        java.util.LinkedHashSet<T> merged = new java.util.LinkedHashSet<>(ascending);
        merged.addAll(descending);
        return new java.util.ArrayList<>(merged);
    }

    static boolean isRetryable(HttpStatusCode status) {
        return status.value() == HttpStatus.TOO_MANY_REQUESTS.value() || status.is5xxServerError();
    }

    static Duration backoffFor(HttpHeaders headers, int attempt) {
        Duration retryAfter = parseRetryAfter(headers);
        Duration backoff = retryAfter != null ? retryAfter : BASE_BACKOFF.multipliedBy(1L << attempt);
        return backoff.compareTo(MAX_BACKOFF) > 0 ? MAX_BACKOFF : backoff;
    }

    private static URI buildUri(UriBuilder uriBuilder, String view, ViewQuery query) {
        uriBuilder.path(VIEW_PATH);
        query.filters().forEach(uriBuilder::queryParam);
        if (query.sort() != null) {
            uriBuilder.queryParam("sort", query.sort());
            uriBuilder.queryParam("direction", query.direction());
        }
        if (query.limit() != null) {
            uriBuilder.queryParam("limit", query.limit());
        }
        return uriBuilder.build(view);
    }

    private static Duration parseRetryAfter(HttpHeaders headers) {
        if (headers == null) {
            return null;
        }
        String value = headers.getFirst(HttpHeaders.RETRY_AFTER);
        if (value == null) {
            return null;
        }
        try {
            return Duration.ofSeconds(Long.parseLong(value.trim()));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static void sleep(Duration backoff) {
        try {
            Thread.sleep(backoff);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Reintento contra SysAcad interrumpido", e);
        }
    }
}
