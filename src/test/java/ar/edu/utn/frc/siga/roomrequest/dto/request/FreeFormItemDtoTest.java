package ar.edu.utn.frc.siga.roomrequest.dto.request;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Reglas {@code @AssertTrue} y {@code @NotNull} de {@link FreeFormItemDto} (parcial fuera de horario, final, conferencia, otro). */
@DisplayName("FreeFormItemDto")
class FreeFormItemDtoTest {

    private static ValidatorFactory factory;
    private static Validator validator;

    @BeforeAll
    static void setUpValidator() {
        factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @AfterAll
    static void tearDownValidator() {
        factory.close();
    }

    @Nested
    @DisplayName("normalización de preferencias")
    class Preferences {

        @Test
        @DisplayName("null queda como lista vacía")
        void nullBecomesEmpty() {
            assertThat(item().preferredClassroomIds(null).build().preferredClassroomIds()).isEmpty();
        }

        @Test
        @DisplayName("la lista se copia y es inmutable")
        void copiedAndUnmodifiable() {
            List<Long> source = new ArrayList<>(List.of(1L, 2L));
            FreeFormItemDto dto = item().preferredClassroomIds(source).build();
            source.add(3L);
            assertThat(dto.preferredClassroomIds()).containsExactly(1L, 2L);
            assertThatThrownBy(() -> dto.preferredClassroomIds().add(9L))
                    .isInstanceOf(UnsupportedOperationException.class);
        }

        @Test
        @DisplayName("aula repetida: rechazada")
        void repeated() {
            assertThat(messages(item().preferredClassroomIds(List.of(1L, 1L)).build()))
                    .contains("No se puede repetir un aula en las preferencias");
        }
    }

    @Nested
    @DisplayName("campos obligatorios")
    class Required {

        @Test
        @DisplayName("payload base: válido")
        void baseIsValid() {
            assertThat(violations(item().build())).isEmpty();
        }

        @Test
        @DisplayName("fecha ausente / en el pasado: rechazada")
        void date() {
            assertThat(violations(item().date(null).build())).isNotEmpty();
            assertThat(violations(item().date(LocalDate.now().minusDays(1)).build())).isNotEmpty();
        }

        @Test
        @DisplayName("hora inicio/fin ausente: rechazada")
        void times() {
            assertThat(violations(item().startTime(null).build())).isNotEmpty();
            assertThat(violations(item().endTime(null).build())).isNotEmpty();
        }

        @Test
        @DisplayName("estimado ausente / negativo: rechazado")
        void estimated() {
            assertThat(violations(item().estimated(null).build())).isNotEmpty();
            assertThat(violations(item().estimated(-1).build())).isNotEmpty();
        }
    }

    @Nested
    @DisplayName("rango horario")
    class TimeRange {

        @Test
        @DisplayName("fin igual o anterior al inicio: rechazado")
        void invertedOrEqual() {
            assertThat(messages(item().startTime(LocalTime.of(10, 0)).endTime(LocalTime.of(10, 0)).build()))
                    .contains("La hora de fin debe ser posterior a la hora de inicio");
            assertThat(messages(item().startTime(LocalTime.of(12, 0)).endTime(LocalTime.of(10, 0)).build()))
                    .contains("La hora de fin debe ser posterior a la hora de inicio");
        }

        @Test
        @DisplayName("duration() sale del rango")
        void duration() {
            assertThat(item().startTime(LocalTime.of(10, 0)).endTime(LocalTime.of(12, 30)).build().duration())
                    .isEqualTo(Duration.ofMinutes(150));
        }
    }

    @Nested
    @DisplayName("computadoras y software")
    class Computers {

        @Test
        @DisplayName("pide computadoras sin cantidad / cantidad sin pedirlas: rechazado")
        void computerCount() {
            assertThat(messages(item().requiresComputers(true).computerCount(null).build()))
                    .contains("Debe indicar la cantidad de computadoras si requiere computadoras, y omitirla si no");
            assertThat(messages(item().requiresComputers(false).computerCount(20).build()))
                    .contains("Debe indicar la cantidad de computadoras si requiere computadoras, y omitirla si no");
        }

        @Test
        @DisplayName("software sin computadoras: rechazado")
        void software() {
            assertThat(messages(item().requiresComputers(false).requiredSoftware("Office").build()))
                    .contains("Solo se puede indicar software requerido si requiere computadoras");
        }
    }

    private Set<ConstraintViolation<FreeFormItemDto>> violations(FreeFormItemDto dto) {
        return validator.validate(dto);
    }

    private List<String> messages(FreeFormItemDto dto) {
        return violations(dto).stream().map(ConstraintViolation::getMessage).toList();
    }

    private static Builder item() {
        return new Builder();
    }

    private static final class Builder {
        private Long commissionId = 1L;
        private LocalDate date = LocalDate.now().plusDays(7);
        private LocalTime startTime = LocalTime.of(10, 0);
        private LocalTime endTime = LocalTime.of(12, 0);
        private Integer estimated = 35;
        private Integer classroomCount = 1;
        private Boolean requiresProjector = false;
        private Boolean requiresComputers = false;
        private Integer computerCount;
        private Boolean requiresExamUsers;
        private String requiredSoftware;
        private String observations;
        private List<Long> preferredClassroomIds = List.of();

        Builder date(LocalDate v) { this.date = v; return this; }
        Builder startTime(LocalTime v) { this.startTime = v; return this; }
        Builder endTime(LocalTime v) { this.endTime = v; return this; }
        Builder estimated(Integer v) { this.estimated = v; return this; }
        Builder requiresComputers(Boolean v) { this.requiresComputers = v; return this; }
        Builder computerCount(Integer v) { this.computerCount = v; return this; }
        Builder requiredSoftware(String v) { this.requiredSoftware = v; return this; }
        Builder preferredClassroomIds(List<Long> v) { this.preferredClassroomIds = v; return this; }

        FreeFormItemDto build() {
            return new FreeFormItemDto(commissionId, date, startTime, endTime, estimated, classroomCount,
                    requiresProjector, requiresComputers, computerCount, requiresExamUsers,
                    requiredSoftware, observations, preferredClassroomIds);
        }
    }
}
