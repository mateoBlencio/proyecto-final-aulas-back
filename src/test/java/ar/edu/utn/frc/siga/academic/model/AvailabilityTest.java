package ar.edu.utn.frc.siga.academic.model;

import static org.assertj.core.api.Assertions.assertThat;

import ar.edu.utn.frc.siga.common.model.UnavailableReason;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("isAvailable: la baja de un padre oculta al hijo sin modificarlo")
class AvailabilityTest {

    private static StudyPlan plan(boolean active) {
        StudyPlan plan = StudyPlan.builder().id(1L).build();
        if (!active) {
            plan.deactivate();
        }
        return plan;
    }

    private static AcademicPeriod period(boolean active) {
        AcademicPeriod period = AcademicPeriod.builder().id(1L).build();
        if (!active) {
            period.deactivate();
        }
        return period;
    }

    private static Subject subject(StudyPlan plan, boolean active) {
        Subject subject = Subject.builder().id(1L).studyPlan(plan).build();
        if (!active) {
            subject.deactivate();
        }
        return subject;
    }

    private static Commission commission(AcademicPeriod period, boolean active) {
        Commission commission = Commission.builder().id(1L).academicPeriod(period).build();
        if (!active) {
            commission.deactivate();
        }
        return commission;
    }

    @Test
    @DisplayName("Subject: disponible solo si ella y su plan están activos")
    void subject() {
        assertThat(subject(plan(true), true).isAvailable()).isTrue();
        assertThat(subject(plan(false), true).isAvailable()).isFalse();
        assertThat(subject(plan(true), false).isAvailable()).isFalse();
        assertThat(subject(plan(false), true).isActive()).isTrue();
    }

    @Test
    @DisplayName("Commission: disponible solo si ella y su período están activos")
    void commission() {
        assertThat(commission(period(true), true).isAvailable()).isTrue();
        assertThat(commission(period(false), true).isAvailable()).isFalse();
        assertThat(commission(period(true), false).isAvailable()).isFalse();
        assertThat(commission(period(false), true).isActive()).isTrue();
    }

    @Test
    @DisplayName("SubjectCommission: disponible solo si el vínculo, la materia y la comisión están disponibles")
    void subjectCommission() {
        Subject okSubject = subject(plan(true), true);
        Commission okCommission = commission(period(true), true);

        assertThat(link(okSubject, okCommission, true).isAvailable()).isTrue();
        assertThat(link(okSubject, okCommission, false).isAvailable()).isFalse();
        assertThat(link(subject(plan(false), true), okCommission, true).isAvailable()).isFalse();
        assertThat(link(subject(plan(true), false), okCommission, true).isAvailable()).isFalse();
        assertThat(link(okSubject, commission(period(false), true), true).isAvailable()).isFalse();
        assertThat(link(okSubject, commission(period(true), false), true).isAvailable()).isFalse();
    }

    private static Commission commission(AcademicPeriod period, boolean active, boolean enabled, String hash) {
        Commission commission = Commission.builder().id(1L).academicPeriod(period)
                .sysacadEnabled(enabled).sysacadHash(hash).build();
        if (!active) {
            commission.deactivate();
        }
        return commission;
    }

    @Test
    @DisplayName("Subject.unavailableReason: null si está disponible")
    void subjectReasonIsNullWhenAvailable() {
        assertThat(subject(plan(true), true).unavailableReason()).isNull();
    }

    @Test
    @DisplayName("Subject.unavailableReason: baja propia es DEACTIVATED")
    void subjectReasonDeactivated() {
        assertThat(subject(plan(true), false).unavailableReason()).isEqualTo(UnavailableReason.DEACTIVATED);
    }

    @Test
    @DisplayName("Subject.unavailableReason: plan dado de baja es PARENT_DEACTIVATED")
    void subjectReasonParentDeactivated() {
        assertThat(subject(plan(false), true).unavailableReason()).isEqualTo(UnavailableReason.PARENT_DEACTIVATED);
    }

    @Test
    @DisplayName("Subject.unavailableReason: baja propia y baja del plan a la vez, gana DEACTIVATED")
    void subjectOwnDeactivationWinsOverParent() {
        assertThat(subject(plan(false), false).unavailableReason()).isEqualTo(UnavailableReason.DEACTIVATED);
    }

    @Test
    @DisplayName("Commission.unavailableReason: null si está activa, su período activo y vigente")
    void commissionReasonIsNullWhenAvailable() {
        assertThat(commission(period(true), true, true, "h").unavailableReason()).isNull();
    }

    @Test
    @DisplayName("Commission.unavailableReason: baja propia es DEACTIVATED")
    void commissionReasonDeactivated() {
        assertThat(commission(period(true), false, true, "h").unavailableReason())
                .isEqualTo(UnavailableReason.DEACTIVATED);
    }

    @Test
    @DisplayName("Commission.unavailableReason: período dado de baja es PARENT_DEACTIVATED")
    void commissionReasonParentDeactivated() {
        assertThat(commission(period(false), true, true, "h").unavailableReason())
                .isEqualTo(UnavailableReason.PARENT_DEACTIVATED);
    }

    @Test
    @DisplayName("Commission.unavailableReason: con hash y flag apagado es NOT_IN_SYSACAD")
    void commissionReasonNotInSysacad() {
        Commission commission = commission(period(true), true, false, "h");

        assertThat(commission.unavailableReason()).isEqualTo(UnavailableReason.NOT_IN_SYSACAD);
        assertThat(commission.isAvailable()).isFalse();
    }

    @Test
    @DisplayName("Commission: una comisión manual (hash nulo, flag apagado) sigue disponible")
    void manualCommissionStaysAvailable() {
        Commission commission = commission(period(true), true, false, null);

        assertThat(commission.unavailableReason()).isNull();
        assertThat(commission.isAvailable()).isTrue();
    }

    @Test
    @DisplayName("Commission.unavailableReason: baja propia y baja del período a la vez, gana DEACTIVATED")
    void commissionOwnDeactivationWinsOverParent() {
        assertThat(commission(period(false), false, true, "h").unavailableReason())
                .isEqualTo(UnavailableReason.DEACTIVATED);
    }

    @Test
    @DisplayName("Commission.unavailableReason: baja del período gana sobre no estar vigente")
    void commissionParentDeactivationWinsOverNotInSysacad() {
        assertThat(commission(period(false), true, false, "h").unavailableReason())
                .isEqualTo(UnavailableReason.PARENT_DEACTIVATED);
    }

    @Test
    @DisplayName("Commission.unavailableReason: baja propia gana sobre no estar vigente")
    void commissionOwnDeactivationWinsOverNotInSysacad() {
        assertThat(commission(period(true), false, false, "h").unavailableReason())
                .isEqualTo(UnavailableReason.DEACTIVATED);
    }


    private static SubjectCommission link(Subject subject, Commission commission, boolean active) {
        SubjectCommission link = SubjectCommission.builder().subject(subject).commission(commission).build();
        if (!active) {
            link.deactivate();
        }
        return link;
    }
}
