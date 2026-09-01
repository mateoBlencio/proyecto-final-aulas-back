package ar.edu.utn.frc.siga.roomrequest.dto.request;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@link ScheduledItemDto} (cambio de aula, parcial en horario de clases). No lleva franja horaria ni
 * fuerza fecha/día: qué combinación es válida lo decide el handler según el tipo; acá sólo se cubren
 * las reglas transversales.
 */
@DisplayName("ScheduledItemDto")
class ScheduledItemDtoTest {

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

    @Test
    @DisplayName("payload base (una fecha, sin franja horaria): válido")
    void baseIsValid() {
        assertThat(violations(item().build())).isEmpty();
    }

    @Test
    @DisplayName("preferencias: null → vacía; copiada e inmutable; repetida rechazada")
    void preferences() {
        assertThat(item().preferredClassroomIds(null).build().preferredClassroomIds()).isEmpty();

        List<Long> source = new ArrayList<>(List.of(1L, 2L));
        ScheduledItemDto dto = item().preferredClassroomIds(source).build();
        source.add(3L);
        assertThat(dto.preferredClassroomIds()).containsExactly(1L, 2L);
        assertThatThrownBy(() -> dto.preferredClassroomIds().add(9L))
                .isInstanceOf(UnsupportedOperationException.class);

        assertThat(messages(item().preferredClassroomIds(List.of(1L, 1L)).build()))
                .contains("No se puede repetir un aula en las preferencias");
    }

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

    @Test
    @DisplayName("estimado negativo: rechazado")
    void negativeEstimated() {
        assertThat(violations(item().estimated(-1).build())).isNotEmpty();
    }

    @Test
    @DisplayName("cantidad de aulas: 0 y más de 100 rechazadas; 100 admitida")
    void classroomCountBounds() {
        assertThat(violations(item().classroomCount(0).build())).isNotEmpty();
        assertThat(violations(item().classroomCount(101).build())).isNotEmpty();
        assertThat(violations(item().classroomCount(100).build())).isEmpty();
    }

    @Test
    @DisplayName("fecha en el pasado: rechazada (fecha ausente sí se admite: la usa el subtipo por día)")
    void pastDate() {
        assertThat(violations(new ScheduledItemDto(LocalDate.now().minusDays(1), null, null, 1,
                false, false, null, null, null, null, List.of()))).isNotEmpty();
        assertThat(violations(new ScheduledItemDto(null, DayOfWeek.TUESDAY, null, 1,
                false, false, null, null, null, null, List.of()))).isEmpty();
    }

    private Set<ConstraintViolation<ScheduledItemDto>> violations(ScheduledItemDto dto) {
        return validator.validate(dto);
    }

    private List<String> messages(ScheduledItemDto dto) {
        return violations(dto).stream().map(ConstraintViolation::getMessage).toList();
    }

    private static Builder item() {
        return new Builder();
    }

    private static final class Builder {
        private LocalDate date = LocalDate.now().plusDays(7);
        private DayOfWeek dayOfWeek;
        private Integer estimated;
        private Integer classroomCount = 1;
        private Boolean requiresProjector = false;
        private Boolean requiresComputers = false;
        private Integer computerCount;
        private Boolean requiresExamUsers;
        private String requiredSoftware;
        private String observations;
        private List<Long> preferredClassroomIds = List.of();

        Builder estimated(Integer v) { this.estimated = v; return this; }
        Builder classroomCount(Integer v) { this.classroomCount = v; return this; }
        Builder requiresComputers(Boolean v) { this.requiresComputers = v; return this; }
        Builder computerCount(Integer v) { this.computerCount = v; return this; }
        Builder requiredSoftware(String v) { this.requiredSoftware = v; return this; }
        Builder preferredClassroomIds(List<Long> v) { this.preferredClassroomIds = v; return this; }

        ScheduledItemDto build() {
            return new ScheduledItemDto(date, dayOfWeek, estimated, classroomCount, requiresProjector,
                    requiresComputers, computerCount, requiresExamUsers, requiredSoftware, observations,
                    preferredClassroomIds);
        }
    }
}
