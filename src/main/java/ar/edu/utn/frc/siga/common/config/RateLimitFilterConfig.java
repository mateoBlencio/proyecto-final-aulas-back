package ar.edu.utn.frc.siga.common.config;

import ar.edu.utn.frc.siga.common.web.RateLimitFilter;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

/**
 * Orden del filtro — crítico, entre el {@code ForwardedHeaderFilter} y la cadena de Security:
 *
 * <pre>
 * ForwardedHeaderFilter (HIGHEST_PRECEDENCE)
 *     &lt; RateLimitFilter (HIGHEST_PRECEDENCE + 1)      &lt;- lee getRemoteAddr() ya resuelto, corta antes de gastar CPU en JWT/DB
 *         &lt; FilterChainProxy de Spring Security (SecurityProperties.DEFAULT_FILTER_ORDER = -100)
 * </pre>
 *
 * Debe correr después del {@code ForwardedHeaderFilter} (si no, {@code getRemoteAddr()}
 * devolvería la IP del proxy, no la del cliente, y el rate-limit por IP se rompería en
 * silencio) y antes del {@code FilterChainProxy} de Spring Security, para cortar tráfico
 * abusivo antes de gastar CPU en validar JWT o tocar la base.
 */
@Configuration
@EnableConfigurationProperties({GeneralRateLimitProperties.class, CorsProperties.class})
public class RateLimitFilterConfig {

    @Bean
    public FilterRegistrationBean<RateLimitFilter> rateLimitFilterRegistration(
            GeneralRateLimitProperties properties, CorsProperties corsProperties) {
        FilterRegistrationBean<RateLimitFilter> registration =
                new FilterRegistrationBean<>(new RateLimitFilter(properties, corsProperties));
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 1);
        return registration;
    }
}
