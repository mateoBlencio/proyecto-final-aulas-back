package ar.edu.utn.frc.siga.settings.model;

import lombok.Getter;
import org.springframework.modulith.NamedInterface;

@Getter
@NamedInterface("api")
public enum SettingKey {

    OPTIMIZER_UNIMPROVED_SECONDS_LIMIT(
            "optimizer.unimprovedSecondsLimit", SettingType.LONG, RiskLevel.SAFE, "optimizer",
            "10", "0", "3600", null),
    OPTIMIZER_WEIGHT_OVERCROWDING(
            "optimizer.weights.overcrowding", SettingType.INT, RiskLevel.SAFE, "optimizer",
            "100000", "0", "1000000", null),
    OPTIMIZER_WEIGHT_SAME_COMMISSION_DIFF_ROOM(
            "optimizer.weights.sameCommissionDiffRoom", SettingType.INT, RiskLevel.SAFE, "optimizer",
            "2000", "0", "1000000", null),
    OPTIMIZER_WEIGHT_SAME_COMMISSION_DIFF_BUILDING(
            "optimizer.weights.sameCommissionDiffBuilding", SettingType.INT, RiskLevel.SAFE, "optimizer",
            "4000", "0", "1000000", null),
    OPTIMIZER_WEIGHT_UNUSED_CAPACITY(
            "optimizer.weights.unusedCapacity", SettingType.INT, RiskLevel.SAFE, "optimizer",
            "1", "0", "1000000", null),
    OPTIMIZER_SOLVER_SECONDS_SPENT_LIMIT(
            "optimizer.solverSecondsSpentLimit", SettingType.LONG, RiskLevel.SAFE, "optimizer",
            "300", "1", "3600", null),
    PREVIEW_DEFAULT_TIME_LIMIT_SECONDS(
            "preview.defaultTimeLimitSeconds", SettingType.INT, RiskLevel.SAFE, "preview",
            "30", "1", "3600", null),
    PREVIEW_TTL_MINUTES(
            "preview.ttlMinutes", SettingType.LONG, RiskLevel.SAFE, "preview",
            "30", "1", "1440", null),
    EVENTS_HOURS_START(
            "events.hours.start", SettingType.TIME, RiskLevel.SAFE, "events",
            "08:00", null, null, null),
    EVENTS_HOURS_END(
            "events.hours.end", SettingType.TIME, RiskLevel.SAFE, "events",
            "23:00", null, null, null),
    OPTIMIZER_CONSTRAINT_MINIMIZE_OVERCROWDING_ENABLED(
            "optimizer.constraints.minimizeOvercrowding.enabled", SettingType.BOOLEAN, RiskLevel.ADVANCED, "optimizer",
            "true", null, null, "Deshabilitar una restricción cambia la política de asignación"),
    OPTIMIZER_CONSTRAINT_MINIMIZE_UNUSED_CAPACITY_ENABLED(
            "optimizer.constraints.minimizeUnusedCapacity.enabled", SettingType.BOOLEAN, RiskLevel.ADVANCED, "optimizer",
            "true", null, null, "Deshabilitar una restricción cambia la política de asignación"),
    OPTIMIZER_CONSTRAINT_PREFER_SAME_ROOM_SAME_COMMISSION_ENABLED(
            "optimizer.constraints.preferSameRoomSameCommission.enabled", SettingType.BOOLEAN, RiskLevel.ADVANCED, "optimizer",
            "true", null, null, "Deshabilitar una restricción cambia la política de asignación"),
    OPTIMIZER_CONSTRAINT_PREFER_SAME_BUILDING_SAME_COMMISSION_ENABLED(
            "optimizer.constraints.preferSameBuildingSameCommission.enabled", SettingType.BOOLEAN, RiskLevel.ADVANCED, "optimizer",
            "true", null, null, "Deshabilitar una restricción cambia la política de asignación");

    private final String key;
    private final SettingType type;
    private final RiskLevel risk;
    private final String category;
    private final String defaultValue;
    private final String min;
    private final String max;
    private final String warning;

    SettingKey(String key, SettingType type, RiskLevel risk, String category,
               String defaultValue, String min, String max, String warning) {
        this.key = key;
        this.type = type;
        this.risk = risk;
        this.category = category;
        this.defaultValue = defaultValue;
        this.min = min;
        this.max = max;
        this.warning = warning;
    }

    public static SettingKey fromKey(String key) {
        for (SettingKey settingKey : values()) {
            if (settingKey.key.equals(key)) {
                return settingKey;
            }
        }
        throw new IllegalArgumentException("Clave de configuración inválida: " + key);
    }
}
