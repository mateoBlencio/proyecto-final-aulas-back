package ar.edu.utn.frc.siga.sysacad.internal.config;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("SysacadProperties: validación de configuración")
class SysacadPropertiesTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    private static SysacadProperties valid() {
        SysacadProperties properties = new SysacadProperties();
        properties.setBaseUrl("https://sysacad.example.test");
        properties.setApiKey("clave");
        return properties;
    }

    @Test
    @DisplayName("una configuración con https, clave y umbral por defecto es válida")
    void validConfiguration() {
        assertThat(validator.validate(valid())).isEmpty();
        assertThat(valid().getSync().minValidRowsPercent()).isEqualTo(50);
    }

    @Test
    @DisplayName("una base-url http o una api-key en blanco se rechazan")
    void rejectsHttpUrlAndBlankKey() {
        SysacadProperties properties = valid();
        properties.setBaseUrl("http://sysacad.example.test");
        properties.setApiKey(" ");

        assertThat(validator.validate(properties)).extracting(v -> v.getPropertyPath().toString())
                .containsExactlyInAnyOrder("baseUrl", "apiKey");
    }

    @Test
    @DisplayName("el umbral de filas válidas fuera de 0..100 se rechaza; 0 y 100 son válidos")
    void rejectsThresholdOutOfRange() {
        SysacadProperties properties = valid();

        properties.setSync(new SysacadProperties.Sync("0 0 3 * * SUN", 101, null, null, null));
        assertThat(validator.validate(properties)).hasSize(1);
        properties.setSync(new SysacadProperties.Sync("0 0 3 * * SUN", -1, null, null, null));
        assertThat(validator.validate(properties)).hasSize(1);
        properties.setSync(new SysacadProperties.Sync("0 0 3 * * SUN", 0, null, null, null));
        assertThat(validator.validate(properties)).isEmpty();
        properties.setSync(new SysacadProperties.Sync("0 0 3 * * SUN", 100, null, null, null));
        assertThat(validator.validate(properties)).isEmpty();
    }

    @Test
    @DisplayName("autogestion habilitada exige destino, legajo y tipo de usuario; deshabilitada no exige nada")
    void autogestionRequiresTargetWhenEnabled() {
        SysacadProperties properties = valid();

        properties.setAutogestion(new SysacadProperties.Autogestion(true, " ", 5, 581, 100, "U", 14, "0 0 23 * * *"));
        assertThat(validator.validate(properties)).hasSize(1);
        properties.setAutogestion(new SysacadProperties.Autogestion(true, "TEST", 5, null, 100, "U", 14, "0 0 23 * * *"));
        assertThat(validator.validate(properties)).hasSize(1);
        properties.setAutogestion(new SysacadProperties.Autogestion(true, "TEST", 5, 581, null, "U", 14, "0 0 23 * * *"));
        assertThat(validator.validate(properties)).hasSize(1);
        properties.setAutogestion(new SysacadProperties.Autogestion(true, "TEST", 5, 581, 100, "", 14, "0 0 23 * * *"));
        assertThat(validator.validate(properties)).hasSize(1);
        properties.setAutogestion(new SysacadProperties.Autogestion(true, "TEST", 5, 581, 100, "U", 0, "0 0 23 * * *"));
        assertThat(validator.validate(properties)).hasSize(1);
        properties.setAutogestion(new SysacadProperties.Autogestion(true, "TEST", 5, 581, 100, "U", 14, "0 0 23 * * *"));
        assertThat(validator.validate(properties)).isEmpty();
        properties.setAutogestion(new SysacadProperties.Autogestion(false, null, null, null, null, null, 14, "0 0 23 * * *"));
        assertThat(validator.validate(properties)).isEmpty();
    }

    @Test
    @DisplayName("el filtro del sync exige courseCode, specialty y subject juntos")
    void syncScopeRequiresAllThree() {
        SysacadProperties properties = valid();

        properties.setSync(new SysacadProperties.Sync(null, 50, "TEST", 5, null));
        assertThat(validator.validate(properties)).hasSize(1);
        properties.setSync(new SysacadProperties.Sync(null, 50, null, 5, 581));
        assertThat(validator.validate(properties)).hasSize(1);
        properties.setSync(new SysacadProperties.Sync(null, 50, "TEST", 5, 581));
        assertThat(validator.validate(properties)).isEmpty();
        properties.setSync(new SysacadProperties.Sync(null, 50, "", null, null));
        assertThat(validator.validate(properties)).isEmpty();
    }
}
