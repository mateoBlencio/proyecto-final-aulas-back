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

/**
 * Las reglas {@code @AssertTrue} del DTO sólo corren vía Bean Validation, es
 * decir cuando la request entra por el controller con {@code @Valid}. Los tests
 * de integración por service construyen el record a mano y las saltean, así que
 * se cubren acá.
 */
@DisplayName("CreateRoomRequestItemDto")
class CreateRoomRequestItemDtoTest {

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
    @DisplayName("normalización en el constructor")
    class Normalization {

        @Test
        @DisplayName("preferredClassroomIds en null queda como lista vacía, no null")
        void nullPreferences_becomeEmptyList() {
            assertThat(item().preferredClassroomIds(null).build().preferredClassroomIds()).isEmpty();
        }

        @Test
        @DisplayName("la lista se copia: mutar la original no cambia el DTO")
        void preferences_areDefensivelyCopied() {
            List<Integer> source = new ArrayList<>(List.of(1, 2));
            CreateRoomRequestItemDto dto = item().preferredClassroomIds(source).build();

            source.add(3);

            assertThat(dto.preferredClassroomIds()).containsExactly(1, 2);
        }

        @Test
        @DisplayName("la lista del DTO es inmutable")
        void preferences_areUnmodifiable() {
            CreateRoomRequestItemDto dto = item().preferredClassroomIds(List.of(1)).build();

            assertThatThrownBy(() -> dto.preferredClassroomIds().add(2))
                    .isInstanceOf(UnsupportedOperationException.class);
        }

        @Test
        @DisplayName("se admite un id inexistente: el 404 claro lo da el validator, no la deserialización")
        void preferences_admitUnknownIds() {
            assertThat(item().preferredClassroomIds(List.of(999_999)).build().preferredClassroomIds())
                    .containsExactly(999_999);
        }
    }

    @Nested
    @DisplayName("duration()")
    class DurationDerivation {

        @Test
        @DisplayName("deriva la duración del rango que carga el docente")
        void derivesDurationFromRange() {
            CreateRoomRequestItemDto dto = item()
                    .startTime(LocalTime.of(10, 0))
                    .endTime(LocalTime.of(12, 30))
                    .build();

            assertThat(dto.duration()).isEqualTo(Duration.ofMinutes(150));
        }
    }

    @Nested
    @DisplayName("rango horario")
    class TimeRange {

        @Test
        @DisplayName("fin posterior a inicio: válido")
        void endAfterStart_isValid() {
            assertThat(violations(item().build())).isEmpty();
        }

        @Test
        @DisplayName("fin igual al inicio: rechazado")
        void endEqualToStart_isRejected() {
            CreateRoomRequestItemDto dto = item()
                    .startTime(LocalTime.of(10, 0))
                    .endTime(LocalTime.of(10, 0))
                    .build();

            assertThat(messages(dto)).contains("La hora de fin debe ser posterior a la hora de inicio");
        }

        @Test
        @DisplayName("fin anterior al inicio: rechazado")
        void endBeforeStart_isRejected() {
            CreateRoomRequestItemDto dto = item()
                    .startTime(LocalTime.of(12, 0))
                    .endTime(LocalTime.of(10, 0))
                    .build();

            assertThat(messages(dto)).contains("La hora de fin debe ser posterior a la hora de inicio");
        }
    }

    @Nested
    @DisplayName("computadoras y software")
    class Computers {

        @Test
        @DisplayName("requiere computadoras con cantidad: válido")
        void computersWithCount_isValid() {
            assertThat(violations(item().requiresComputers(true).computerCount(20).build())).isEmpty();
        }

        @Test
        @DisplayName("requiere computadoras sin cantidad: rechazado")
        void computersWithoutCount_isRejected() {
            CreateRoomRequestItemDto dto = item().requiresComputers(true).computerCount(null).build();

            assertThat(messages(dto)).contains(
                    "Debe indicar la cantidad de computadoras si requiere computadoras, y omitirla si no");
        }

        @Test
        @DisplayName("cantidad de computadoras sin pedirlas: rechazado")
        void countWithoutComputers_isRejected() {
            CreateRoomRequestItemDto dto = item().requiresComputers(false).computerCount(20).build();

            assertThat(messages(dto)).contains(
                    "Debe indicar la cantidad de computadoras si requiere computadoras, y omitirla si no");
        }

        @Test
        @DisplayName("requiresComputers ausente equivale a no pedirlas")
        void nullComputers_behaveAsFalse() {
            assertThat(violations(item().requiresComputers(null).computerCount(null).build())).isEmpty();
        }

        @Test
        @DisplayName("cantidad de computadoras en 0: rechazado por @Min(1)")
        void zeroComputerCount_isRejected() {
            CreateRoomRequestItemDto dto = item().requiresComputers(true).computerCount(0).build();

            assertThat(violations(dto)).isNotEmpty();
        }

        @Test
        @DisplayName("software requerido con computadoras: válido")
        void softwareWithComputers_isValid() {
            CreateRoomRequestItemDto dto = item()
                    .requiresComputers(true)
                    .computerCount(20)
                    .requiredSoftware("Office")
                    .build();

            assertThat(violations(dto)).isEmpty();
        }

        @Test
        @DisplayName("software requerido sin computadoras: rechazado")
        void softwareWithoutComputers_isRejected() {
            CreateRoomRequestItemDto dto = item().requiresComputers(false).requiredSoftware("Office").build();

            assertThat(messages(dto)).contains(
                    "Solo se puede indicar software requerido si requiere computadoras");
        }
    }

    @Nested
    @DisplayName("preferencias de aula")
    class Preferences {

        @Test
        @DisplayName("aulas distintas: válido")
        void distinctClassrooms_areValid() {
            assertThat(violations(item().preferredClassroomIds(List.of(1, 2, 3)).build())).isEmpty();
        }

        @Test
        @DisplayName("aula repetida: rechazada")
        void repeatedClassroom_isRejected() {
            CreateRoomRequestItemDto dto = item().preferredClassroomIds(List.of(1, 2, 1)).build();

            assertThat(messages(dto)).contains("No se puede repetir un aula en las preferencias");
        }
    }

    /**
     * Los largos son espejo del {@code length} de {@code RoomRequestItem}. Sin
     * esto, pasarse revienta al insertar con un 500 en vez de un 400.
     */
    @Nested
    @DisplayName("largo de los campos de texto")
    class TextLengths {

        @Test
        @DisplayName("observations de 1000 caracteres: aceptado")
        void observationsAtLimit_isAccepted() {
            assertThat(violations(item().observations("x".repeat(1000)).build())).isEmpty();
        }

        @Test
        @DisplayName("observations de 1001 caracteres: rechazado antes de llegar a la base")
        void observationsOverLimit_isRejected() {
            assertThat(violations(item().observations("x".repeat(1001)).build())).isNotEmpty();
        }

        @Test
        @DisplayName("requiredSoftware de 255 caracteres: aceptado")
        void requiredSoftwareAtLimit_isAccepted() {
            CreateRoomRequestItemDto dto = item()
                    .requiresComputers(true)
                    .computerCount(20)
                    .requiredSoftware("x".repeat(255))
                    .build();

            assertThat(violations(dto)).isEmpty();
        }

        @Test
        @DisplayName("requiredSoftware de 256 caracteres: rechazado")
        void requiredSoftwareOverLimit_isRejected() {
            CreateRoomRequestItemDto dto = item()
                    .requiresComputers(true)
                    .computerCount(20)
                    .requiredSoftware("x".repeat(256))
                    .build();

            assertThat(violations(dto)).isNotEmpty();
        }
    }

    @Nested
    @DisplayName("campos obligatorios y rangos")
    class RequiredFields {

        @Test
        @DisplayName("fecha en el pasado: rechazada")
        void pastDate_isRejected() {
            assertThat(violations(item().date(LocalDate.now().minusDays(1)).build())).isNotEmpty();
        }

        @Test
        @DisplayName("fecha de hoy: aceptada")
        void today_isAccepted() {
            assertThat(violations(item().date(LocalDate.now()).build())).isEmpty();
        }

        @Test
        @DisplayName("inscriptos negativos: rechazados")
        void negativeEnrolled_isRejected() {
            assertThat(violations(item().enrolled(-1).build())).isNotEmpty();
        }

        @Test
        @DisplayName("cantidad de aulas en 0: rechazada")
        void zeroClassroomCount_isRejected() {
            assertThat(violations(item().classroomCount(0).build())).isNotEmpty();
        }

        @Test
        @DisplayName("fecha ausente: rechazada")
        void nullDate_isRejected() {
            assertThat(violations(item().date(null).build())).isNotEmpty();
        }
    }

    private Set<ConstraintViolation<CreateRoomRequestItemDto>> violations(CreateRoomRequestItemDto dto) {
        return validator.validate(dto);
    }

    private List<String> messages(CreateRoomRequestItemDto dto) {
        return violations(dto).stream().map(ConstraintViolation::getMessage).toList();
    }

    private static ItemBuilder item() {
        return new ItemBuilder();
    }

    /** Pedido válido por defecto; cada test cambia sólo el campo que le interesa. */
    private static final class ItemBuilder {
        private Long commissionId = 1L;
        private LocalDate date = LocalDate.now().plusDays(7);
        private LocalTime startTime = LocalTime.of(10, 0);
        private LocalTime endTime = LocalTime.of(12, 0);
        private Integer enrolled = 30;
        private Integer estimated = 35;
        private Integer classroomCount = 1;
        private Integer currentClassroomId;
        private Boolean requiresProjector = false;
        private Boolean requiresComputers = false;
        private Integer computerCount;
        private Boolean requiresExamUsers;
        private String requiredSoftware;
        private String observations;
        private List<Integer> preferredClassroomIds = List.of();

        ItemBuilder date(LocalDate v) { this.date = v; return this; }
        ItemBuilder startTime(LocalTime v) { this.startTime = v; return this; }
        ItemBuilder endTime(LocalTime v) { this.endTime = v; return this; }
        ItemBuilder enrolled(Integer v) { this.enrolled = v; return this; }
        ItemBuilder classroomCount(Integer v) { this.classroomCount = v; return this; }
        ItemBuilder requiresComputers(Boolean v) { this.requiresComputers = v; return this; }
        ItemBuilder computerCount(Integer v) { this.computerCount = v; return this; }
        ItemBuilder requiredSoftware(String v) { this.requiredSoftware = v; return this; }
        ItemBuilder observations(String v) { this.observations = v; return this; }
        ItemBuilder preferredClassroomIds(List<Integer> v) { this.preferredClassroomIds = v; return this; }

        CreateRoomRequestItemDto build() {
            return new CreateRoomRequestItemDto(
                    commissionId, date, startTime, endTime, enrolled, estimated, classroomCount,
                    currentClassroomId, requiresProjector, requiresComputers, computerCount,
                    requiresExamUsers, requiredSoftware, observations, preferredClassroomIds);
        }
    }
}
