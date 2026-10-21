package ar.edu.utn.frc.siga.allocation.model;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

class AllocationSourceTest {

    @ParameterizedTest
    @CsvSource({"IMPORTED,true", "SYSACAD,true", "MANUAL,false", "AUTOMATIC,false"})
    void allowsPastOccurrences_onlyForImportedAndSysacad(AllocationSource source, boolean expected) {
        assertThat(source.allowsPastOccurrences()).isEqualTo(expected);
    }
}
