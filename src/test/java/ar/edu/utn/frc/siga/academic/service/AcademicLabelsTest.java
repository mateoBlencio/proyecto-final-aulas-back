package ar.edu.utn.frc.siga.academic.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("AcademicLabels")
class AcademicLabelsTest {

    @Test
    @DisplayName("materia y comisión: '{materia} ({comisión})'")
    void subjectAndCode() {
        assertThat(AcademicLabels.subjectCommission("Análisis Matemático I", "1K1"))
                .isEqualTo("Análisis Matemático I (1K1)");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"  "})
    @DisplayName("sin comisión (nula, vacía o en blanco) devuelve solo la materia")
    void subjectWithoutCode(String code) {
        assertThat(AcademicLabels.subjectCommission("Física I", code)).isEqualTo("Física I");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"  "})
    @DisplayName("sin materia (nula, vacía o en blanco) devuelve solo la comisión")
    void codeWithoutSubject(String subject) {
        assertThat(AcademicLabels.subjectCommission(subject, "2K3")).isEqualTo("2K3");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"  "})
    @DisplayName("sin materia ni comisión devuelve null")
    void neither(String blank) {
        assertThat(AcademicLabels.subjectCommission(blank, blank)).isNull();
    }
}
