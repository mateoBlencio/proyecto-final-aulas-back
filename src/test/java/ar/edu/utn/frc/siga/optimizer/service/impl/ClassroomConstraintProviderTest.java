package ar.edu.utn.frc.siga.optimizer.service.impl;

import ai.timefold.solver.core.config.util.ConfigUtils;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("ClassroomConstraintProvider — custom properties de Timefold")
class ClassroomConstraintProviderTest {

    @Test
    @DisplayName("los String de customProperties se convierten a boolean e int en los setters")
    void customPropertiesAreConvertedToPrimitiveTypes() throws Exception {
        ClassroomConstraintProvider provider = new ClassroomConstraintProvider();

        ConfigUtils.applyCustomProperties(provider, "constraintProviderClass",
                Map.of(
                        "minimizeOvercrowdingEnabled", "false",
                        "minimizeUnusedCapacityEnabled", "true",
                        "preferSameRoomSameCommissionEnabled", "false",
                        "preferSameBuildingSameCommissionEnabled", "true",
                        "unusedCapacityWeight", "7"),
                "constraintProviderCustomProperties");

        assertThat(readBoolean(provider, "minimizeOvercrowdingEnabled")).isFalse();
        assertThat(readBoolean(provider, "minimizeUnusedCapacityEnabled")).isTrue();
        assertThat(readBoolean(provider, "preferSameRoomSameCommissionEnabled")).isFalse();
        assertThat(readBoolean(provider, "preferSameBuildingSameCommissionEnabled")).isTrue();
        assertThat(readInt(provider, "unusedCapacityWeight")).isEqualTo(7);
    }

    private static boolean readBoolean(Object target, String fieldName) throws Exception {
        return field(target, fieldName).getBoolean(target);
    }

    private static int readInt(Object target, String fieldName) throws Exception {
        return field(target, fieldName).getInt(target);
    }

    private static Field field(Object target, String fieldName) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        return field;
    }
}
