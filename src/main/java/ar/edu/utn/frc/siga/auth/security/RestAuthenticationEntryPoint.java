package ar.edu.utn.frc.siga.auth.security;

import ar.edu.utn.frc.siga.common.web.ProblemDetailResponseWriter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

@Component
public class RestAuthenticationEntryPoint implements AuthenticationEntryPoint {

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response, AuthenticationException authException)
            throws IOException {
        ProblemDetailResponseWriter.write(response, HttpStatus.UNAUTHORIZED,
                "Unauthorized", "Authentication is required to access this resource");
    }
}
