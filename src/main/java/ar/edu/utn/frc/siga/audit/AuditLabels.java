package ar.edu.utn.frc.siga.audit;

import ar.edu.utn.frc.siga.audit.service.impl.FieldLabels;
import org.springframework.modulith.NamedInterface;

/** Spanish names of enum constants for the modules that write audit texts. */
@NamedInterface("api")
public final class AuditLabels {

    private AuditLabels() {
    }

    /** Visible name of {@code value} (for example "Clase virtual" for {@code VIRTUAL_CLASS}). */
    public static String constant(Enum<?> value) {
        return FieldLabels.constant(value.name());
    }
}
