package ar.edu.utn.frc.siga.solver.dto.response;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class AllocationQualityTest {

    @Test
    void upAq001_optimal_highOccupancy() {
        assertThat(AllocationQuality.of(true, 0, 0.75)).isEqualTo(AllocationQuality.OPTIMAL);
    }

    @Test
    void upAq002_optimal_exactThreshold() {
        assertThat(AllocationQuality.of(true, 0, 0.60)).isEqualTo(AllocationQuality.OPTIMAL);
    }

    @Test
    void upAq003_acceptable_belowThreshold() {
        assertThat(AllocationQuality.of(true, 0, 0.59)).isEqualTo(AllocationQuality.ACCEPTABLE);
    }

    @Test
    void upAq004_acceptable_zeroOccupancy() {
        assertThat(AllocationQuality.of(true, 0, 0.0)).isEqualTo(AllocationQuality.ACCEPTABLE);
    }

    @Test
    void upAq005_poor_overcrowded() {
        assertThat(AllocationQuality.of(true, 5, 1.0)).isEqualTo(AllocationQuality.POOR);
    }

    @Test
    void upAq006_poor_overcrowdingTakesPriority() {
        assertThat(AllocationQuality.of(true, 1, 0.5)).isEqualTo(AllocationQuality.POOR);
    }

    @Test
    void upAq007_unassigned() {
        assertThat(AllocationQuality.of(false, 0, 0.0)).isEqualTo(AllocationQuality.UNASSIGNED);
    }
}
