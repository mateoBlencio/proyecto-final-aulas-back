package ar.edu.utn.frc.siga.auth.security;

import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * JWT stateless de verdad: valida firma + expiración y construye el {@link SecurityUser}
 * directamente desde los claims del token, sin volver a la base de datos en cada request.
 * Esto implica que un cambio de rol o de {@code habilitado} no se refleja hasta que el
 * access token vigente expira (ver plan-seguridad.md, "Filtro y autenticación").
 */
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtService jwtService;

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                     @NonNull HttpServletResponse response,
                                     @NonNull FilterChain filterChain) throws ServletException, IOException {

        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith(BEARER_PREFIX)) {
            String token = header.substring(BEARER_PREFIX.length());

            if (jwtService.isValid(token)) {
                Claims claims = jwtService.parseClaims(token);
                String email = jwtService.extractEmail(claims);

                SecurityUser securityUser = new SecurityUser(null, email, null,
                        jwtService.extractRoles(claims), true);

                UsernamePasswordAuthenticationToken authentication =
                        new UsernamePasswordAuthenticationToken(securityUser, null, securityUser.getAuthorities());
                authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                SecurityContextHolder.getContext().setAuthentication(authentication);
            }
        }

        filterChain.doFilter(request, response);
    }

    /**
     * Re-autenticar también en el dispatch de ERROR. Sin esto, cuando un request autenticado
     * produce un error (400/404/500), Spring MVC hace un forward interno a {@code /error}; como
     * {@code OncePerRequestFilter} se saltea los dispatches de ERROR por default, la auth se
     * perdería y {@code /error} (que exige autenticación) respondería 401, enmascarando el
     * status real. El header Authorization sigue presente en el forward, así que revalidamos.
     */
    @Override
    protected boolean shouldNotFilterErrorDispatch() {
        return false;
    }

    /**
     * Ídem para dispatches ASYNC: si un endpoint pasa a ser asíncrono, la auth debe
     * re-establecerse en el thread del dispatch async para no perderse.
     */
    @Override
    protected boolean shouldNotFilterAsyncDispatch() {
        return false;
    }
}
