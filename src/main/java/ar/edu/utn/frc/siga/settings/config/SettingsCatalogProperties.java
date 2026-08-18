package ar.edu.utn.frc.siga.settings.config;

import ar.edu.utn.frc.siga.settings.model.SettingKey;
import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@ConfigurationProperties(prefix = "siga.settings")
public class SettingsCatalogProperties {

    private List<Definition> definitions = new ArrayList<>();

    @PostConstruct
    void verifyComplete() {
        List<String> missing = new ArrayList<>();
        for (SettingKey key : SettingKey.values()) {
            if (definitions.stream().noneMatch(definition -> key.getKey().equals(definition.getKey()))) {
                missing.add(key.getKey());
            }
        }
        if (!missing.isEmpty()) {
            throw new IllegalStateException(
                    "Catálogo de configuración incompleto en siga.settings.definitions, faltan: " + missing);
        }
    }

    public String defaultValue(SettingKey key) {
        return definitionFor(key).getDefaultValue();
    }

    public String min(SettingKey key) {
        return definitionFor(key).getMin();
    }

    public String max(SettingKey key) {
        return definitionFor(key).getMax();
    }

    private Definition definitionFor(SettingKey key) {
        return definitions.stream()
                .filter(definition -> key.getKey().equals(definition.getKey()))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "Falta metadata de configuración para la clave: " + key.getKey()));
    }

    @Getter
    @Setter
    public static class Definition {
        private String key;
        private String defaultValue;
        private String min;
        private String max;
    }
}
