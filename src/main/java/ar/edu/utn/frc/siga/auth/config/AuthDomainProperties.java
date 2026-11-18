package ar.edu.utn.frc.siga.auth.config;

import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.springframework.stereotype.Component;

@Component
public class AuthDomainProperties {

    private static final String BASE_DOMAIN = "frc.utn.edu.ar";

    private static final Set<String> SUBDOMAINS = Set.of(
            "cbasicas", "civil", "colegio", "computos", "decanato", "egresado", "electrica",
            "electronica", "extension", "industrial", "licenciatura", "mecanica", "metalurgica",
            "nuclear", "org", "posgrado", "punilla", "quimica", "radio", "sa", "sae", "scdt",
            "sistemas", "tecnicatura", "virtual");

    /** Dominios aceptados por igualdad exacta: frc.utn.edu.ar y cada {@code <sub>.frc.utn.edu.ar}. */
    private static final Set<String> ALLOWED_DOMAINS = Stream.concat(
                    Stream.of(BASE_DOMAIN), SUBDOMAINS.stream().map(sub -> sub + "." + BASE_DOMAIN))
            .collect(Collectors.toUnmodifiableSet());

    /**
     * Forma canónica del correo de un usuario de SIGA, para guardarlo, buscarlo y compararlo: sin espacios
     * en los extremos y en minúsculas. Devuelve {@code null} si recibe {@code null}.
     */
    public static String normalizeEmail(String email) {
        return email == null ? null : email.strip().toLowerCase(Locale.ROOT);
    }

    public boolean isAllowedEmail(String email) {
        if (email == null) {
            return false;
        }
        int at = email.indexOf('@');
        if (at <= 0 || at != email.lastIndexOf('@')) {
            return false;
        }
        return ALLOWED_DOMAINS.contains(email.substring(at + 1).toLowerCase(Locale.ROOT));
    }
}
