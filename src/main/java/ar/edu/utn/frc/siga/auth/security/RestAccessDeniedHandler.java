package ar.edu.utn.frc.siga.auth.security;

import ar.edu.utn.frc.siga.common.web.ProblemDetailResponseWriter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

@Component
public class RestAccessDeniedHandler implements AccessDeniedHandler {

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response, AccessDeniedException accessDeniedException)
            throws IOException {
        ProblemDetailResponseWriter.write(response, HttpStatus.FORBIDDEN,
                "Forbidden", "You do not have permission to access this resource");
    }
}
