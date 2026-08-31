package ar.edu.utn.frc.siga.settings.model;

import lombok.Getter;
import org.springframework.modulith.NamedInterface;

@Getter
@NamedInterface("api")
public enum SettingKey {

    OPTIMIZER_UNIMPROVED_SECONDS_LIMIT(
            "optimizer.unimprovedSecondsLimit", SettingType.LONG, RiskLevel.SAFE, "optimizer", null),
    OPTIMIZER_WEIGHT_OVERCROWDING(
            "optimizer.weights.overcrowding", SettingType.INT, RiskLevel.SAFE, "optimizer", null),
    OPTIMIZER_WEIGHT_SAME_COMMISSION_DIFF_ROOM(
            "optimizer.weights.sameCommissionDiffRoom", SettingType.INT, RiskLevel.SAFE, "optimizer", null),
    OPTIMIZER_WEIGHT_SAME_COMMISSION_DIFF_BUILDING(
            "optimizer.weights.sameCommissionDiffBuilding", SettingType.INT, RiskLevel.SAFE, "optimizer", null),
    OPTIMIZER_WEIGHT_UNUSED_CAPACITY(
            "optimizer.weights.unusedCapacity", SettingType.INT, RiskLevel.SAFE, "optimizer", null),
    OPTIMIZER_SOLVER_SECONDS_SPENT_LIMIT(
            "optimizer.solverSecondsSpentLimit", SettingType.LONG, RiskLevel.SAFE, "optimizer", null),
    PREVIEW_DEFAULT_TIME_LIMIT_SECONDS(
            "preview.defaultTimeLimitSeconds", SettingType.INT, RiskLevel.SAFE, "preview", null),
    PREVIEW_TTL_MINUTES(
            "preview.ttlMinutes", SettingType.LONG, RiskLevel.SAFE, "preview", null),
    EVENTS_HOURS_START(
            "events.hours.start", SettingType.TIME, RiskLevel.SAFE, "events", null),
    EVENTS_HOURS_END(
            "events.hours.end", SettingType.TIME, RiskLevel.SAFE, "events", null);

    private final String key;
    private final SettingType type;
    private final RiskLevel risk;
    private final String category;
    private final String warning;

    SettingKey(String key, SettingType type, RiskLevel risk, String category, String warning) {
        this.key = key;
        this.type = type;
        this.risk = risk;
        this.category = category;
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
