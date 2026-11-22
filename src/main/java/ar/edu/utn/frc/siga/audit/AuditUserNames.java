package ar.edu.utn.frc.siga.audit;

import org.springframework.modulith.NamedInterface;

import java.util.Collection;
import java.util.Map;

/**
 * Resolves the full name of the users that appear in the log. The module that owns the users implements it as a
 * bean; {@code audit} never depends on the domain modules.
 */
@NamedInterface("api")
public interface AuditUserNames {

    /** Full name by email. An email with no user is absent from the result. */
    Map<String, String> fullNames(Collection<String> emails);
}
