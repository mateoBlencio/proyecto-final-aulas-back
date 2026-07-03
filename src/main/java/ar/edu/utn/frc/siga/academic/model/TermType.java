package ar.edu.utn.frc.siga.academic.model;

import java.time.LocalDate;
import java.util.Optional;

import lombok.Getter;
import org.springframework.modulith.NamedInterface;

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

    public LocalDate startDate(int year) {
        return switch (this) {
            case ANUAL, PRIMER_CUATRIMESTRE -> LocalDate.of(year, 3, 1);
            case SEGUNDO_CUATRIMESTRE -> LocalDate.of(year, 8, 1);
        };
    }

    public LocalDate endDate(int year) {
        return switch (this) {
            case PRIMER_CUATRIMESTRE -> LocalDate.of(year, 7, 31);
            case ANUAL, SEGUNDO_CUATRIMESTRE -> LocalDate.of(year, 11, 30);
        };
    }

    public static Optional<TermType> fromLabel(String label) {
        for (TermType t : values()) {
            if (t.label.equals(label)) return Optional.of(t);
        }
        return Optional.empty();
    }
}
