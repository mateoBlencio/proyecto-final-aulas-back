package ar.edu.utn.frc.siga.audit.service.impl;

import ar.edu.utn.frc.siga.allocation.model.AllocationSource;
import ar.edu.utn.frc.siga.audit.AuditLabels;
import ar.edu.utn.frc.siga.audit.model.AuditArchiveStatus;
import ar.edu.utn.frc.siga.auth.model.SystemRole;
import ar.edu.utn.frc.siga.common.security.ScopeType;
import ar.edu.utn.frc.siga.events.model.OccurrenceStatus;
import ar.edu.utn.frc.siga.events.model.ReleaseReason;
import ar.edu.utn.frc.siga.events.model.UniqueEventKind;
import ar.edu.utn.frc.siga.roomrequest.model.AcademicScope;
import ar.edu.utn.frc.siga.roomrequest.model.RoomRequestStatus;
import ar.edu.utn.frc.siga.roomrequest.model.RoomRequestType;
import ar.edu.utn.frc.siga.space.model.PermissionMode;
import ar.edu.utn.frc.siga.space.model.PermissionTargetKind;
import ar.edu.utn.frc.siga.space.model.ResourceValueKind;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.time.DayOfWeek;
import java.util.Arrays;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("FieldLabels.constant y AuditLabels")
class FieldLabelsTest {

    /** Constants whose Spanish text is, by coincidence, what the fallback would print too. */
    private static final Set<String> COINCIDES_WITH_HUMANIZE = Set.of("EXAMEN_FINAL");

    private static Stream<Arguments> everyShownEnumConstant() {
        return Stream.of(AllocationSource.class, OccurrenceStatus.class, ReleaseReason.class, UniqueEventKind.class,
                        SystemRole.class, ScopeType.class,
                        RoomRequestStatus.class, RoomRequestType.class, AcademicScope.class, PermissionMode.class,
                        PermissionTargetKind.class, ResourceValueKind.class, AuditArchiveStatus.class,
                        DayOfWeek.class)
                .flatMap(type -> Arrays.stream(type.getEnumConstants()).map(value -> Arguments.of(type.getSimpleName(), value)));
    }

    /** What the fallback would print for the constant: "VIRTUAL_CLASS" becomes "Virtual class". */
    private static String humanized(String constant) {
        String lower = constant.replace('_', ' ').toLowerCase(Locale.ROOT);
        return Character.toUpperCase(lower.charAt(0)) + lower.substring(1);
    }

    @ParameterizedTest(name = "{0}.{1}")
    @MethodSource("everyShownEnumConstant")
    @DisplayName("toda constante que el registro puede mostrar tiene texto propio y sin guion bajo")
    void constant_everyEnumConstant_hasOwnSpanishText(String type, Enum<?> value) {
        String label = FieldLabels.constant(value.name());

        assertThat(label).isNotBlank().doesNotContain("_").isNotEqualTo(value.name());
        if (value.name().contains("_") && !COINCIDES_WITH_HUMANIZE.contains(value.name())) {
            assertThat(label).as("%s.%s cae en el humanize en vez del mapa explícito", type, value)
                    .isNotEqualTo(humanized(value.name()));
        }
    }

    @ParameterizedTest(name = "{0}.{1}")
    @MethodSource("everyShownEnumConstant")
    @DisplayName("AuditLabels.constant da el mismo texto que FieldLabels.constant para el nombre de la constante")
    void auditLabels_constant_matchesFieldLabels(String type, Enum<?> value) {
        assertThat(AuditLabels.constant(value)).isEqualTo(FieldLabels.constant(value.name()));
    }

    @Test
    @DisplayName("textos concretos: VIRTUAL_CLASS es 'Clase virtual' y los roles salen en español")
    void constant_knownTexts() {
        assertThat(AuditLabels.constant(ReleaseReason.VIRTUAL_CLASS)).isEqualTo("Clase virtual");
        assertThat(AuditLabels.constant(SystemRole.SUBSECRETARIA)).isEqualTo("Subsecretaría");
        assertThat(AuditLabels.constant(SystemRole.AUXILIAR_AULICO)).isEqualTo("Auxiliar áulico");
        assertThat(AuditLabels.constant(SystemRole.CONSULTA)).isEqualTo("Consulta");
        assertThat(AuditLabels.constant(DayOfWeek.MONDAY)).isEqualTo("Lunes");
    }

    @Test
    @DisplayName("una constante fuera del mapa cae en el humanize en vez de fallar")
    void constant_unknown_fallsBackToHumanize() {
        assertThat(FieldLabels.constant("SOME_NEW_VALUE")).isEqualTo("Some new value");
    }
}
