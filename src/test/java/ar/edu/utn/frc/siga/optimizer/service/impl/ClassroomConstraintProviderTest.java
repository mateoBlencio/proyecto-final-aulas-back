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
    @DisplayName("los String de customProperties se convierten a int en los setters de pesos")
    void customPropertiesAreConvertedToPrimitiveTypes() throws Exception {
        ClassroomConstraintProvider provider = new ClassroomConstraintProvider();

        ConfigUtils.applyCustomProperties(provider, "constraintProviderClass",
                Map.of(
                        "overcrowdingWeight", "55555",
                        "sameCommissionDiffRoomWeight", "3000",
                        "sameCommissionDiffBuildingWeight", "6000",
                        "unusedCapacityWeight", "7"),
                "constraintProviderCustomProperties");

        assertThat(readInt(provider, "overcrowdingWeight")).isEqualTo(55555);
        assertThat(readInt(provider, "sameCommissionDiffRoomWeight")).isEqualTo(3000);
        assertThat(readInt(provider, "sameCommissionDiffBuildingWeight")).isEqualTo(6000);
        assertThat(readInt(provider, "unusedCapacityWeight")).isEqualTo(7);
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
