package ar.edu.utn.frc.siga.sysacad.internal.service.impl;

import ar.edu.utn.frc.siga.sysacad.internal.client.dto.RawCommission;
import ar.edu.utn.frc.siga.sysacad.internal.client.dto.RawSchedule;
import ar.edu.utn.frc.siga.sysacad.internal.client.dto.RawSubject;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("SyncScope: sync restringido a un curso, especialidad y materia")
class SyncScopeTest {

    private final SyncScope scope = new SyncScope(" test ", 5, 581);

    @Test
    @DisplayName("deja solo la materia de esa especialidad, aunque otra especialidad tenga el mismo código")
    void keepsOnlySubjectOfSpecialty() {
        RawSubject kept = new RawSubject(5, 2023, 581, "Gestión de la Calidad (Elec.)");
        RawSubject sameCodeOtherSpecialty = new RawSubject(27, 2023, 581, "Hidrógeno y Power-to-X Renovable (Elec.)");
        RawSubject otherSubject = new RawSubject(5, 2023, 402, "Ingeniería y Calidad de Software");

        assertThat(scope.subjects(List.of(kept, sameCodeOtherSpecialty, otherSubject))).containsExactly(kept);
    }

    @Test
    @DisplayName("deja solo la comisión con ese curso (sin distinguir mayúsculas ni espacios)")
    void keepsOnlyCommissionOfCourse() {
        RawCommission kept = new RawCommission("TEST  ", 5, 2023, 581, 2026, 5);
        RawCommission otherCourse = new RawCommission("5V1", 5, 2023, 581, 2026, 1);
        RawCommission otherSpecialty = new RawCommission("TEST", 27, 2023, 581, 2026, 2);
        RawCommission otherSubject = new RawCommission("TEST", 5, 2023, 402, 2026, 3);

        assertThat(scope.commissions(List.of(kept, otherCourse, otherSpecialty, otherSubject)))
                .containsExactly(kept);
    }

    @Test
    @DisplayName("deja solo las filas de horario de ese curso, especialidad y materia")
    void keepsOnlySchedulesOfCourse() {
        RawSchedule kept = schedule("TEST", 5, 581);

        assertThat(scope.schedules(List.of(kept, schedule("5V1", 5, 581), schedule("TEST", 27, 581),
                schedule("TEST", 5, 402)))).containsExactly(kept);
    }

    private static RawSchedule schedule(String course, Integer specialty, Integer subject) {
        return new RawSchedule(course, 1, 805, 15, "Edif", 2, 1, "A", "1", "10:30", "12:45", "10:30-12:45", 135,
                specialty, "Sistemas", 2023, subject, "Materia", 30);
    }
}
