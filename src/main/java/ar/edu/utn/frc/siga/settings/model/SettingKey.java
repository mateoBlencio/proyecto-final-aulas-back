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
    OPTIMIZER_WEIGHT_SAME_EVENT_DIFF_BUILDING(
            "optimizer.weights.sameEventDiffBuilding", SettingType.INT, RiskLevel.SAFE, "optimizer", null),
    OPTIMIZER_WEIGHT_MISSING_PROJECTOR(
            "optimizer.weights.missingProjector", SettingType.INT, RiskLevel.SAFE, "optimizer", null),
    OPTIMIZER_WEIGHT_MISSING_COMPUTER(
            "optimizer.weights.missingComputer", SettingType.INT, RiskLevel.SAFE, "optimizer", null),
    OPTIMIZER_WEIGHT_UNUSED_CAPACITY(
            "optimizer.weights.unusedCapacity", SettingType.INT, RiskLevel.SAFE, "optimizer", null),
    OPTIMIZER_SOLVER_SECONDS_SPENT_LIMIT(
            "optimizer.solverSecondsSpentLimit", SettingType.LONG, RiskLevel.SAFE, "optimizer", null),
    PREVIEW_DEFAULT_TIME_LIMIT_SECONDS(
            "preview.defaultTimeLimitSeconds", SettingType.INT, RiskLevel.SAFE, "preview", null),
    PREVIEW_TTL_MINUTES(
            "preview.ttlMinutes", SettingType.LONG, RiskLevel.SAFE, "preview", null),
    PREVIEW_SUGGESTION_TIME_LIMIT_SECONDS(
            "preview.suggestionTimeLimitSeconds", SettingType.INT, RiskLevel.SAFE, "preview", null),
    EVENTS_HOURS_START(
            "events.hours.start", SettingType.TIME, RiskLevel.SAFE, "events", null),
    EVENTS_HOURS_END(
            "events.hours.end", SettingType.TIME, RiskLevel.SAFE, "events", null),
    ALLOCATION_MAX_OVERLAP_MINUTES(
            "allocation.maxOverlapMinutes", SettingType.INT, RiskLevel.ADVANCED, "allocation",
            "Dos eventos distintos pueden compartir un aula durante este margen, solo en asignación "
                    + "manual y dejando un comentario. Subirlo esconde choques reales de horario; "
                    + "0 vuelve al rechazo estricto."),
    NOTIFICATIONS_ENABLED(
            "notifications.enabled", SettingType.BOOLEAN, RiskLevel.SAFE, "notifications",
            "Apagarlo corta todo aviso automático: los pedidos resueltos quedan sin comunicar al docente."),
    NOTIFICATIONS_CHANNEL_EMAIL_ENABLED(
            "notifications.channel.email.enabled", SettingType.BOOLEAN, RiskLevel.SAFE, "notifications", null),
    ROOM_REQUESTS_MAX_CLASSROOM_COUNT(
            "roomRequests.maxClassroomCount", SettingType.INT, RiskLevel.SAFE, "roomRequests", null),
    ROOM_REQUESTS_MAX_ESTIMATED(
            "roomRequests.maxEstimated", SettingType.INT, RiskLevel.SAFE, "roomRequests", null),
    ROOM_REQUESTS_MAX_COMPUTER_COUNT(
            "roomRequests.maxComputerCount", SettingType.INT, RiskLevel.SAFE, "roomRequests", null),
    ROOM_REQUESTS_ALERT_DAYS_BEFORE_DATE(
            "roomRequests.alertDaysBeforeDate", SettingType.INT, RiskLevel.SAFE, "roomRequests", null),
    ROOM_REQUESTS_ACCEPTANCE_BUSINESS_HOURS(
            "roomRequests.acceptance.businessHours", SettingType.INT, RiskLevel.SAFE, "roomRequests",
            "Cada día hábil (lunes a viernes sin feriado) suma 24 horas al plazo que tiene el docente para "
                    + "responder: 48 son dos días hábiles, no dos jornadas de 8 horas."),
    ROOM_REQUESTS_ACCEPTANCE_HOURS_BEFORE_EVENT(
            "roomRequests.acceptance.hoursBeforeEvent", SettingType.INT, RiskLevel.SAFE, "roomRequests", null),
    SYSACAD_WRITE_MODE(
            "sysacad.write.mode", SettingType.ENUM, RiskLevel.ADVANCED, "sysacad",
            "OFF no envía nada y SIGA sigue lo que dice SysAcad. "
                    + "SANDBOX conserva en SIGA los cambios de aula y registra los envíos que haría, sin llamar a SysAcad: al pasar a ON se envían los acumulados. "
                    + "ON modifica aulas reales en SysAcad: dev y test comparten la misma base de SysAcad."),
    SYSACAD_AUTOGESTION_MODE(
            "sysacad.autogestion.mode", SettingType.ENUM, RiskLevel.ADVANCED, "sysacad",
            "ON publica el mensaje de autogestión a los alumnos de todas las comisiones afectadas. "
                    + "TEST_COMMISSIONS lo limita a las comisiones de prueba."),
    SYSACAD_WRITE_JOB_DELAY_MINUTES(
            "sysacad.write.jobDelayMinutes", SettingType.LONG, RiskLevel.SAFE, "sysacad", null),
    SYSACAD_WRITE_VIEW_READ_INTERVAL_MINUTES(
            "sysacad.write.viewReadIntervalMinutes", SettingType.LONG, RiskLevel.SAFE, "sysacad", null);

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
