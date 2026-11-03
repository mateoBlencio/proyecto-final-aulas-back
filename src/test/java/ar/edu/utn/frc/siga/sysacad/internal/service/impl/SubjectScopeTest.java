package ar.edu.utn.frc.siga.sysacad.internal.service.impl;

import ar.edu.utn.frc.siga.common.util.Lazy;
import ar.edu.utn.frc.siga.sysacad.internal.client.dto.RawCommission;
import ar.edu.utn.frc.siga.sysacad.internal.client.dto.RawSchedule;
import ar.edu.utn.frc.siga.sysacad.internal.client.dto.RawSubject;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("SubjectScope: sync restringido a una materia por nombre")
class SubjectScopeTest {

    private static final RawSubject TEST_SUBJECT = new RawSubject(17, 94, 600, "(TEST) Gestión de la Calidad (Elec.)");
    private static final RawSubject OTHER_SUBJECT = new RawSubject(17, 94, 519, "Análisis Matemático I");

    private final SubjectScope scope = new SubjectScope(
            " (test) gestión de la calidad (elec.) ", Lazy.of(() -> List.of(TEST_SUBJECT, OTHER_SUBJECT)));

    @Test
    @DisplayName("deja solo la materia con ese nombre, sin distinguir mayúsculas ni espacios")
    void keepsOnlyNamedSubject() {
        assertThat(scope.subjects(List.of(TEST_SUBJECT, OTHER_SUBJECT))).containsExactly(TEST_SUBJECT);
    }

    @Test
    @DisplayName("deja solo las comisiones de esa materia")
    void keepsOnlyCommissionsOfSubject() {
        RawCommission kept = new RawCommission("1K1", 17, 94, 600, 2026, 1);
        RawCommission otherSubject = new RawCommission("1K2", 17, 94, 519, 2026, 2);
        RawCommission otherPlan = new RawCommission("1K3", 17, 95, 600, 2026, 3);

        assertThat(scope.commissions(List.of(kept, otherSubject, otherPlan))).containsExactly(kept);
    }

    @Test
    @DisplayName("deja solo las filas de horario de esa materia")
    void keepsOnlySchedulesOfSubject() {
        RawSchedule kept = schedule(17, 94, 600);
        RawSchedule other = schedule(17, 94, 519);

        assertThat(scope.schedules(List.of(kept, other))).containsExactly(kept);
    }

    @Test
    @DisplayName("si ninguna materia tiene ese nombre, no deja nada")
    void keepsNothingWhenNameMatchesNoSubject() {
        SubjectScope empty = new SubjectScope("Inexistente", Lazy.of(() -> List.of(OTHER_SUBJECT)));

        assertThat(empty.subjects(List.of(OTHER_SUBJECT))).isEmpty();
        assertThat(empty.schedules(List.of(schedule(17, 94, 519)))).isEmpty();
    }

    private static RawSchedule schedule(Integer specialty, Integer plan, Integer subject) {
        return new RawSchedule("1K1", 1, 805, 15, "Edif", 2, 1, "A", "1", "10:30", "12:45", "10:30-12:45", 135,
                specialty, "Sistemas", plan, subject, "Materia", 30);
    }
}
