package ar.edu.utn.frc.siga.common.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.junit.jupiter.api.Test;

class SoftDeletableEntityTest {

    static class Dummy extends SoftDeletableEntity {}

    @Test
    void newInstanceIsActive() {
        Dummy dummy = new Dummy();

        assertThat(dummy.isActive()).isTrue();
        assertThat(dummy.isDeleted()).isFalse();
        assertThat(dummy.getDeletedAt()).isNull();
    }

    @Test
    void deactivateMarksAsDeleted() {
        Dummy dummy = new Dummy();

        dummy.deactivate();

        assertThat(dummy.getDeletedAt()).isNotNull();
        assertThat(dummy.isActive()).isFalse();
        assertThat(dummy.isDeleted()).isTrue();
    }

    @Test
    void deactivateWithInstantUsesTheGivenInstant() {
        Dummy dummy = new Dummy();
        Instant when = Instant.now().minus(3, ChronoUnit.DAYS);

        dummy.deactivate(when);

        assertThat(dummy.getDeletedAt()).isEqualTo(when);
    }

    @Test
    void deactivateIsIdempotentAndKeepsTheFirstInstant() {
        Dummy dummy = new Dummy();
        Instant first = Instant.now().minus(2, ChronoUnit.DAYS);
        Instant second = Instant.now();

        dummy.deactivate(first);
        dummy.deactivate(second);

        assertThat(dummy.getDeletedAt()).isEqualTo(first);
    }

    @Test
    void activateClearsDeletedAt() {
        Dummy dummy = new Dummy();
        dummy.deactivate();

        dummy.activate();

        assertThat(dummy.getDeletedAt()).isNull();
        assertThat(dummy.isActive()).isTrue();
        assertThat(dummy.isDeleted()).isFalse();
    }

    @Test
    void activateOnAnAlreadyActiveEntityIsANoOp() {
        Dummy dummy = new Dummy();

        dummy.activate();

        assertThat(dummy.getDeletedAt()).isNull();
        assertThat(dummy.isActive()).isTrue();
    }
}
