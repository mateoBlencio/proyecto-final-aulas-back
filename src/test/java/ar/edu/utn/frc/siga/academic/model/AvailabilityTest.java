package ar.edu.utn.frc.siga.academic.model;

import static org.assertj.core.api.Assertions.assertThat;

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

    private static SubjectCommission link(Subject subject, Commission commission, boolean active) {
        SubjectCommission link = SubjectCommission.builder().subject(subject).commission(commission).build();
        if (!active) {
            link.deactivate();
        }
        return link;
    }
}
