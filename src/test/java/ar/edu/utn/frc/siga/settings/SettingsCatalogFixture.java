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
                definition("optimizer.weights.unusedCapacity", "1", "0", "1000000"),
                definition("optimizer.solverSecondsSpentLimit", "300", "1", "3600"),
                definition("preview.defaultTimeLimitSeconds", "30", "1", "3600"),
                definition("preview.ttlMinutes", "30", "1", "1440"),
                definition("events.hours.start", "08:00", null, null),
                definition("events.hours.end", "23:00", null, null)));
        return properties;
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
