package ar.edu.utn.frc.siga.common.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.List;

/**
 * Habilita CORS para todos los endpoints de la API, permitiendo los orígenes configurados
 * en la propiedad {@code cors.allowed-origins} (lista separada por comas).
 */
@Configuration
public class CorsConfig implements WebMvcConfigurer {

    @Value("#{'${cors.allowed-origins}'.split(',')}")
    private List<String> allowedOrigins;

    /**
     * Registra el mapeo CORS global: acepta los métodos HTTP usados por la API, cualquier
     * header, credenciales, y cachea la respuesta de preflight por una hora.
     */
    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**")
                .allowedOrigins(allowedOrigins.toArray(new String[0]))
                .allowedMethods("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS")
                .allowedHeaders("*")
                .allowCredentials(true)
                .maxAge(3600);
    }
}
