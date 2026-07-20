package ar.edu.utn.frc.siga.academic.model;

import java.time.LocalDate;
import java.util.Locale;
import java.util.Optional;

import lombok.Getter;
import org.springframework.modulith.NamedInterface;

/**
 * Modalidad de dictado de una materia/comisión (anual o por cuatrimestre); deriva las
 * fechas estándar de inicio/fin de un {@link AcademicPeriod} para un año dado. No está
 * mapeado a columna: es lógica de apoyo usada, por ejemplo, en la importación desde Excel.
 */
@Getter
@NamedInterface("api")
public enum TermType {
    ANUAL("Anual", 0),
    PRIMER_CUATRIMESTRE("1 Cuat.", 1),
    SEGUNDO_CUATRIMESTRE("2 Cuat.", 2);

    private final String label;
    private final int semester;

    TermType(String label, int semester) {
        this.label = label;
        this.semester = semester;
    }

    /** Fecha de inicio estándar del período para este tipo de dictado en el año dado. */
    public LocalDate startDate(int year) {
        return switch (this) {
            case ANUAL, PRIMER_CUATRIMESTRE -> LocalDate.of(year, 3, 1);
            case SEGUNDO_CUATRIMESTRE -> LocalDate.of(year, 8, 1);
        };
    }

    /** Fecha de fin estándar del período para este tipo de dictado en el año dado. */
    public LocalDate endDate(int year) {
        return switch (this) {
            case PRIMER_CUATRIMESTRE -> LocalDate.of(year, 7, 31);
            case ANUAL, SEGUNDO_CUATRIMESTRE -> LocalDate.of(year, 11, 30);
        };
    }

    /**
     * Resuelve el {@link TermType} a partir de su etiqueta legible (p. ej. "1 Cuat."), usada al importar desde
     * Excel. La comparación es tolerante a mayúsculas/minúsculas, espacios y punto final, para no romper con
     * datos de origen inconsistentes.
     */
    public static Optional<TermType> fromLabel(String label) {
        if (label == null) return Optional.empty();
        String normalized = normalize(label);
        for (TermType t : values()) {
            if (normalize(t.label).equals(normalized)) return Optional.of(t);
        }
        return Optional.empty();
    }

    private static String normalize(String s) {
        String n = s.trim().toLowerCase(Locale.ROOT);
        return n.endsWith(".") ? n.substring(0, n.length() - 1) : n;
    }
}
