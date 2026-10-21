package ar.edu.utn.frc.siga.academic.dto.response;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

@DisplayName("CommissionResponseDto - yearLevel")
class CommissionResponseDtoTest {

    private static CommissionResponseDto withCourseCode(String courseCode) {
        return new CommissionResponseDto(1L, courseCode, null, null);
    }

    @ParameterizedTest(name = "{0} -> {1}")
    @CsvSource({"3K1A, 3", "1K5, 1", "5S1, 5", "9X9, 9"})
    @DisplayName("yearLevel es el primer dígito 1-9 del código de curso")
    void yearLevelFromFirstDigit(String courseCode, int expected) {
        assertThat(withCourseCode(courseCode).yearLevel()).isEqualTo(expected);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"K1001", "0K1", "TEST"})
    @DisplayName("yearLevel es null si el código no empieza con un dígito 1-9")
    void yearLevelNullWhenNoLevelDigit(String courseCode) {
        assertThat(withCourseCode(courseCode).yearLevel()).isNull();
    }
}
