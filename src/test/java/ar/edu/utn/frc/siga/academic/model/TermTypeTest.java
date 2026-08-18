package ar.edu.utn.frc.siga.academic.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("TermType")
class TermTypeTest {

    @Test
    @DisplayName("ANUAL arranca el 1 de marzo y termina el 30 de noviembre")
    void anualStartsMarch1AndEndsNovember30() {
        assertThat(TermType.ANUAL.startDate(2026)).isEqualTo(LocalDate.of(2026, 3, 1));
        assertThat(TermType.ANUAL.endDate(2026)).isEqualTo(LocalDate.of(2026, 11, 30));
    }

    @Test
    @DisplayName("PRIMER_CUATRIMESTRE arranca el 1 de marzo y termina el 31 de julio")
    void primerCuatrimestreStartsMarch1AndEndsJuly31() {
        assertThat(TermType.PRIMER_CUATRIMESTRE.startDate(2026)).isEqualTo(LocalDate.of(2026, 3, 1));
        assertThat(TermType.PRIMER_CUATRIMESTRE.endDate(2026)).isEqualTo(LocalDate.of(2026, 7, 31));
    }

    @Test
    @DisplayName("SEGUNDO_CUATRIMESTRE arranca el 1 de agosto y termina el 30 de noviembre")
    void segundoCuatrimestreStartsAugust1AndEndsNovember30() {
        assertThat(TermType.SEGUNDO_CUATRIMESTRE.startDate(2026)).isEqualTo(LocalDate.of(2026, 8, 1));
        assertThat(TermType.SEGUNDO_CUATRIMESTRE.endDate(2026)).isEqualTo(LocalDate.of(2026, 11, 30));
    }

    @Test
    @DisplayName("cada TermType expone su número de semestre: 0/1/2")
    void exposesSemesterNumber() {
        assertThat(TermType.ANUAL.getSemester()).isZero();
        assertThat(TermType.PRIMER_CUATRIMESTRE.getSemester()).isEqualTo(1);
        assertThat(TermType.SEGUNDO_CUATRIMESTRE.getSemester()).isEqualTo(2);
    }

    @Test
    @DisplayName("fromLabel resuelve por match exacto de etiqueta")
    void fromLabelResolvesExactMatch() {
        assertThat(TermType.fromLabel("Anual")).contains(TermType.ANUAL);
        assertThat(TermType.fromLabel("1 Cuat.")).contains(TermType.PRIMER_CUATRIMESTRE);
        assertThat(TermType.fromLabel("2 Cuat.")).contains(TermType.SEGUNDO_CUATRIMESTRE);
    }

    @Test
    @DisplayName("fromLabel con una etiqueta desconocida devuelve Optional vacío")
    void fromLabelWithUnknownLabelReturnsEmpty() {
        assertThat(TermType.fromLabel("Trimestral")).isEqualTo(Optional.empty());
    }

    @Test
    @DisplayName("fromLabel tolera mayúsculas, espacios y punto final")
    void fromLabelToleratesCaseWhitespaceAndTrailingDot() {
        assertThat(TermType.fromLabel("anual")).contains(TermType.ANUAL);
        assertThat(TermType.fromLabel("1 Cuat")).contains(TermType.PRIMER_CUATRIMESTRE);
        assertThat(TermType.fromLabel(" Anual")).contains(TermType.ANUAL);
    }

    @Test
    @DisplayName("fromLabel(null) devuelve Optional vacío")
    void fromLabelWithNullReturnsEmpty() {
        assertThat(TermType.fromLabel(null)).isEmpty();
    }

    @Test
    @DisplayName("fromLabel con una etiqueta que no corresponde a ningún tipo sigue sin matchear")
    void fromLabelWithUnrelatedLabelStillReturnsEmpty() {
        assertThat(TermType.fromLabel("Trimestral")).isEmpty();
    }
}
