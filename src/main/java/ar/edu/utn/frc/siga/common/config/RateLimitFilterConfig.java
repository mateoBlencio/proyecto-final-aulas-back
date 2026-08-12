package ar.edu.utn.frc.siga.common.config;

import ar.edu.utn.frc.siga.common.web.RateLimitFilter;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

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
