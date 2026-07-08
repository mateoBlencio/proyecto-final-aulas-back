package ar.edu.utn.frc.siga.common.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;

/**
 * Escribe un {@link ProblemDetail} directo en la response para los puntos del filter chain
 * (401/403/429) que corren antes de que el {@code @RestControllerAdvice} pueda intervenir.
 */
public final class ProblemDetailResponseWriter {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private ProblemDetailResponseWriter() {
    }

    public static void write(HttpServletResponse response, HttpStatus status, String title, String detail)
            throws IOException {
        ProblemDetail problem = ProblemDetail.forStatus(status);
        problem.setTitle(title);
        problem.setDetail(detail);

        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.getWriter().write(OBJECT_MAPPER.writeValueAsString(problem));
    }
}
