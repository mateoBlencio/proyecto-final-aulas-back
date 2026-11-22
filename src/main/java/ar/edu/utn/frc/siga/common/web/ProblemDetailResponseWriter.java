package ar.edu.utn.frc.siga.common.web;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URI;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.json.ProblemDetailJacksonMixin;
import tools.jackson.databind.json.JsonMapper;

/**
 * Escribe un ProblemDetail con la misma forma que {@code GlobalExceptionHandler}: el conversor de
 * Spring MVC usa Jackson 3 con {@link ProblemDetailJacksonMixin} (sin nulos, propiedades aplanadas).
 */
public final class ProblemDetailResponseWriter {

    private static final JsonMapper JSON_MAPPER = JsonMapper.builder()
            .addMixIn(ProblemDetail.class, ProblemDetailJacksonMixin.class)
            .build();

    private ProblemDetailResponseWriter() {
    }

    public static void write(HttpServletRequest request, HttpServletResponse response, HttpStatus status,
                             String title, String detail) throws IOException {
        ProblemDetail problem = ProblemDetail.forStatus(status);
        problem.setTitle(title);
        problem.setDetail(detail);
        problem.setInstance(URI.create(request.getRequestURI()));

        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        // Bytes UTF-8 directos: con getWriter() Tomcat codifica en ISO-8859-1 si no hay charset en el Content-Type.
        response.getOutputStream().write(JSON_MAPPER.writeValueAsBytes(problem));
    }
}
