package ar.edu.utn.frc.siga.auth.config;

import jakarta.annotation.PostConstruct;
import java.nio.charset.StandardCharsets;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "siga.jwt")
public class JwtProperties {

    private static final int MIN_SECRET_BYTES = 32;

    /** Sin default: el arranque falla si no está seteada (salvo en dev-local). */
    private String secret;

    private int accessExpirationMinutes = 20;

    private int refreshExpirationDays = 30;

    /** Ventana de gracia para tolerar reintentos de red de un refresh recién rotado. */
    private int refreshGraceSeconds = 10;

    @PostConstruct
    public void validate() {
        if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < MIN_SECRET_BYTES) {
            throw new IllegalStateException(
                    "siga.jwt.secret debe estar seteada y tener al menos 32 bytes (256 bits) para HS256");
        }
    }
}
