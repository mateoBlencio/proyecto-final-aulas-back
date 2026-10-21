package ar.edu.utn.frc.siga.common.web;

import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.stereotype.Component;

/**
 * Warns at startup when TRUSTED_PROXIES is empty in a deployed profile. Without it every client
 * shares the reverse proxy's IP, so the per-IP rate limits act on the whole institution.
 * Setting TRUSTED_PROXIES in dev and test is a mandatory deploy step.
 */
@Slf4j
@Component
class TrustedProxiesStartupCheck {

    private static final Set<String> DEPLOYED_PROFILES = Set.of("dev", "test", "prod");

    private final String internalProxies;
    private final Environment environment;

    TrustedProxiesStartupCheck(@Value("${server.tomcat.remoteip.internal-proxies:}") String internalProxies,
                               Environment environment) {
        this.internalProxies = internalProxies;
        this.environment = environment;
    }

    boolean isMisconfigured() {
        return internalProxies.isBlank()
                && environment.acceptsProfiles(Profiles.of(DEPLOYED_PROFILES.toArray(String[]::new)));
    }

    @EventListener(ApplicationReadyEvent.class)
    void warnIfMisconfigured() {
        if (isMisconfigured()) {
            log.warn("TRUSTED_PROXIES vacío en un perfil desplegado: la IP del cliente es la del proxy y los"
                    + " rate limits por IP afectan a todos los usuarios. Configurar TRUSTED_PROXIES.");
        }
    }
}
