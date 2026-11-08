package ar.edu.utn.frc.siga.settings;

import ar.edu.utn.frc.siga.settings.config.SettingsCatalogProperties;
import ar.edu.utn.frc.siga.settings.config.SettingsCatalogProperties.Definition;

import java.util.List;

public final class SettingsCatalogFixture {

    private SettingsCatalogFixture() {
    }

    public static SettingsCatalogProperties catalog() {
        SettingsCatalogProperties properties = new SettingsCatalogProperties();
        properties.setDefinitions(List.of(
                definition("optimizer.unimprovedSecondsLimit", "10", "0", "3600"),
                definition("optimizer.weights.overcrowding", "100000", "0", "1000000"),
                definition("optimizer.weights.sameCommissionDiffRoom", "2000", "0", "1000000"),
                definition("optimizer.weights.sameCommissionDiffBuilding", "4000", "0", "1000000"),
                definition("optimizer.weights.sameEventDiffBuilding", "8000", "0", "1000000"),
                definition("optimizer.weights.missingProjector", "10000", "0", "1000000"),
                definition("optimizer.weights.missingComputer", "20000", "0", "1000000"),
                definition("optimizer.weights.unusedCapacity", "1", "0", "1000000"),
                definition("optimizer.solverSecondsSpentLimit", "300", "1", "3600"),
                definition("preview.defaultTimeLimitSeconds", "30", "1", "3600"),
                definition("preview.ttlMinutes", "30", "1", "1440"),
                definition("preview.suggestionTimeLimitSeconds", "2", "1", "60"),
                definition("events.hours.start", "08:00", null, null),
                definition("events.hours.end", "23:00", null, null),
                definition("allocation.maxOverlapMinutes", "40", "0", null),
                definition("notifications.enabled", "true", null, null),
                definition("notifications.channel.email.enabled", "true", null, null),
                definition("roomRequests.maxClassroomCount", "20", "1", "100"),
                definition("roomRequests.maxEstimated", "500", "1", "10000"),
                definition("roomRequests.maxComputerCount", "500", "1", "1000"),
                definition("roomRequests.alertDaysBeforeDate", "3", "0", "60"),
                definition("roomRequests.acceptance.businessHours", "48", "1", "720"),
                definition("roomRequests.acceptance.hoursBeforeEvent", "24", "0", "720"),
                enumDefinition("sysacad.write.mode", "OFF", "OFF", "DRY_RUN", "ON"),
                enumDefinition("sysacad.autogestion.mode", "OFF", "OFF", "TEST_COMMISSIONS", "ON"),
                definition("sysacad.write.jobDelayMinutes", "5", "1", "1440"),
                definition("sysacad.write.viewReadIntervalMinutes", "60", "5", "1440"),
                enumDefinition("sysacad.sync.dayOfWeek", "SUN", "MON", "TUE", "WED", "THU", "FRI", "SAT", "SUN"),
                definition("sysacad.sync.time", "03:00", null, null)));
        return properties;
    }

    private static Definition enumDefinition(String key, String defaultValue, String... options) {
        Definition definition = definition(key, defaultValue, null, null);
        definition.setOptions(List.of(options));
        return definition;
    }

    private static Definition definition(String key, String defaultValue, String min, String max) {
        Definition definition = new Definition();
        definition.setKey(key);
        definition.setDefaultValue(defaultValue);
        definition.setMin(min);
        definition.setMax(max);
        return definition;
    }
}
