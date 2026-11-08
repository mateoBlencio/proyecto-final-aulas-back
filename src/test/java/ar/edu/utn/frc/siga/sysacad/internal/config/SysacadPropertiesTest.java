package ar.edu.utn.frc.siga.sysacad.internal.config;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.PropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.time.Duration;
import java.util.List;

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

        properties.setSync(new SysacadProperties.Sync("0 0 3 * * SUN", 101, SysacadProperties.Sync.DEFAULT_ZONE));
        assertThat(validator.validate(properties)).hasSize(1);
        properties.setSync(new SysacadProperties.Sync("0 0 3 * * SUN", -1, SysacadProperties.Sync.DEFAULT_ZONE));
        assertThat(validator.validate(properties)).hasSize(1);
        properties.setSync(new SysacadProperties.Sync("0 0 3 * * SUN", 0, SysacadProperties.Sync.DEFAULT_ZONE));
        assertThat(validator.validate(properties)).isEmpty();
        properties.setSync(new SysacadProperties.Sync("0 0 3 * * SUN", 100, SysacadProperties.Sync.DEFAULT_ZONE));
        assertThat(validator.validate(properties)).isEmpty();
    }

    @Test
    @DisplayName("los valores movidos al yaml tienen los defaults de siempre")
    void movedValuesHaveDefaults() {
        SysacadProperties properties = valid();

        assertThat(properties.getSync().zone()).isEqualTo(java.time.ZoneId.of("America/Argentina/Buenos_Aires"));
        assertThat(properties.getWrite().afterSyncMargin()).isEqualTo(Duration.ofHours(1));
        assertThat(properties.getWrite().noCronRetryDelay()).isEqualTo(Duration.ofDays(1));
        assertThat(properties.getWrite().alertMailMaxLines()).isEqualTo(30);
        assertThat(properties.getCommissionMessagePublisherLegajo()).isZero();
        assertThat(properties.getCommissionMessagePublisherUserType()).isEqualTo("S");
    }

    @Test
    @DisplayName("el yaml liga la zona del sync y los datos del mensaje a comisión")
    void yamlBindsMovedValues() throws IOException {
        List<PropertySource<?>> sources = new YamlPropertySourceLoader()
                .load("application", new ClassPathResource("application.yaml"));
        StandardEnvironment environment = new StandardEnvironment();
        sources.forEach(environment.getPropertySources()::addLast);
        environment.getPropertySources().addFirst(new org.springframework.core.env.MapPropertySource("env",
                java.util.Map.of("SYSACAD_ENABLED", "false", "SYSACAD_API_KEY", "clave")));

        SysacadProperties bound = Binder.get(environment).bind("siga.sysacad", SysacadProperties.class).get();

        assertThat(bound.getSync().zone()).isEqualTo(java.time.ZoneId.of("America/Argentina/Buenos_Aires"));
        assertThat(bound.getCommissionMessagePublisherLegajo()).isZero();
        assertThat(bound.getCommissionMessagePublisherUserType()).isEqualTo("S");
        assertThat(bound.getCommissionMessageCommissions())
                .containsExactly(new SysacadProperties.MessageCommission(5, 2023, 581, 5));
    }

    @Test
    @DisplayName("sin comisiones configuradas la lista queda vacía; una comisión con un campo nulo se rechaza")
    void messageCommissionsDefaultEmptyAndRejectNulls() {
        assertThat(valid().getCommissionMessageCommissions()).isEmpty();

        SysacadProperties properties = valid();
        properties.setCommissionMessageCommissions(
                List.of(new SysacadProperties.MessageCommission(5, 2023, 581, null)));

        assertThat(validator.validate(properties)).extracting(v -> v.getPropertyPath().toString())
                .containsExactly("commissionMessageCommissions[0].commission");
    }

    @Test
    @DisplayName("el modo de escritura es OFF por defecto")
    void writeModeDefaultsToOff() {
        assertThat(valid().getWrite().mode()).isEqualTo(SysacadWriteMode.OFF);
    }

    @Test
    @DisplayName("Write.of(OFF) coincide con los defaults del bloque write")
    void writeOfMatchesDefaults() {
        assertThat(SysacadProperties.Write.of(SysacadWriteMode.OFF)).isEqualTo(valid().getWrite());
        assertThat(SysacadProperties.Write.of(SysacadWriteMode.ON).mode()).isEqualTo(SysacadWriteMode.ON);
    }

    @Test
    @DisplayName("bound sin el bloque write, queda en OFF con los defaults de Write.of")
    void bindingWithoutWriteBlockDefaultsToOff() {
        StandardEnvironment environment = new StandardEnvironment();
        environment.getPropertySources().addFirst(new org.springframework.core.env.MapPropertySource("test",
                java.util.Map.of("siga.sysacad.base-url", "https://sysacad.example.test",
                        "siga.sysacad.api-key", "clave")));

        SysacadProperties bound = Binder.get(environment).bind("siga.sysacad", SysacadProperties.class).get();

        assertThat(bound.getWrite()).isEqualTo(SysacadProperties.Write.of(SysacadWriteMode.OFF));
    }

    @Test
    @DisplayName("el bloque write de application.yaml liga con los valores del plan")
    void yamlWriteBlockBindsPlanValues() throws IOException {
        List<PropertySource<?>> sources = new YamlPropertySourceLoader()
                .load("application", new ClassPathResource("application.yaml"));
        StandardEnvironment environment = new StandardEnvironment();
        sources.forEach(environment.getPropertySources()::addLast);
        environment.getPropertySources().addFirst(new org.springframework.core.env.MapPropertySource("env",
                java.util.Map.of("SYSACAD_ENABLED", "false", "SYSACAD_API_KEY", "clave")));

        SysacadProperties bound = Binder.get(environment).bind("siga.sysacad", SysacadProperties.class).get();

        assertThat(bound.getWrite()).isEqualTo(new SysacadProperties.Write(
                SysacadWriteMode.OFF, Duration.ofMinutes(5), Duration.ofHours(1), 5, 3,
                List.of(Duration.ofMinutes(5), Duration.ofMinutes(15), Duration.ofMinutes(30), Duration.ofHours(1)),
                Duration.ofSeconds(15), Duration.ofSeconds(2), Duration.ofHours(1), Duration.ofDays(1),
                30, 30, Duration.ofDays(7), Duration.ofHours(24),
                Duration.ofHours(1), Duration.ofDays(1), 30));
    }

    @Test
    @DisplayName("max-attempts en 0 falla la validación; en 1 es válido")
    void maxAttemptsBelowOneIsRejected() {
        SysacadProperties properties = valid();
        SysacadProperties.Write base = SysacadProperties.Write.of(SysacadWriteMode.ON);

        properties.setWrite(withMaxAttempts(base, 0));
        assertThat(validator.validate(properties)).extracting(v -> v.getPropertyPath().toString())
                .containsExactly("write.maxAttempts");
        properties.setWrite(withMaxAttempts(base, 1));
        assertThat(validator.validate(properties)).isEmpty();
    }

    private static SysacadProperties.Write withMaxAttempts(SysacadProperties.Write w, int maxAttempts) {
        return new SysacadProperties.Write(w.mode(), w.jobDelay(), w.viewReadInterval(), maxAttempts,
                w.maxConsecutiveFailures(), w.retryBackoff(), w.readTimeout(), w.inCallRetryDelay(),
                w.outageAlertAfter(), w.outageAlertRepeat(), w.maxSendsPerRun(), w.maxRequestsPerMinute(),
                w.recentSendWindow(), w.waitingVisibleBefore(), w.afterSyncMargin(), w.noCronRetryDelay(),
                w.alertMailMaxLines());
    }
}
