package ar.edu.utn.frc.siga.ci;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Extrae el paso "Check env file" de cd-dev.yml y cd-test.yml y lo corre con bash contra un {@code .env} y un
 * {@code docker-compose.deploy.yml} simulados, en un directorio temporal.
 *
 * <p>Si el entorno no tiene bash (se prueba Git Bash de Windows y el {@code bash} del PATH) el test se saltea con
 * una Assumption: el paso solo corre en el runner Linux del deploy, así que no se exige bash en la máquina de
 * desarrollo.
 */
@DisplayName("Paso 'Check env file' de los workflows de deploy")
class DeployEnvCheckWorkflowTest {

    private static final String STEP_NAME = "Check env file";
    private static final Path WORKFLOWS = Path.of(".github", "workflows");
    private static final List<String> BASH_CANDIDATES = List.of(
            "C:\\Program Files\\Git\\bin\\bash.exe", "C:\\Program Files\\Git\\usr\\bin\\bash.exe", "bash");

    private static final String COMPOSE_TEMPLATE = """
            services:
              backend-dev:
                environment:
                  DB_PASSWORD: ${DB_PASSWORD}
                  JWT_SECRET: ${JWT_SECRET}
                  SYSACAD_API_KEY: ${SYSACAD_API_KEY}
                  TRUSTED_PROXIES: ${TRUSTED_PROXIES:-}
              backend-test:
                environment:
                  DB_PASSWORD: ${DB_PASSWORD}
                  JWT_SECRET: ${JWT_SECRET}
                  SYSACAD_API_KEY: ${SYSACAD_API_KEY}
                  TRUSTED_PROXIES: ${TRUSTED_PROXIES:-}
            """;

    @TempDir
    Path tempDir;

    @ParameterizedTest(name = "{0}: con TRUSTED_PROXIES definida el chequeo pasa")
    @ValueSource(strings = {"cd-dev.yml", "cd-test.yml"})
    @DisplayName("con TRUSTED_PROXIES definida termina con rc 0")
    void passesWhenTrustedProxiesIsSet(String workflow) throws Exception {
        Result result = run(workflow, """
                DB_PASSWORD=x
                JWT_SECRET=y
                SYSACAD_API_KEY=
                TRUSTED_PROXIES=10.0.0.0/8
                """);

        assertThat(result.exitCode()).as(result.output()).isZero();
    }

    @ParameterizedTest(name = "{0}: sin TRUSTED_PROXIES el chequeo falla")
    @ValueSource(strings = {"cd-dev.yml", "cd-test.yml"})
    @DisplayName("sin TRUSTED_PROXIES en el .env termina con rc 1 y avisa que falta")
    void failsWhenTrustedProxiesIsMissing(String workflow) throws Exception {
        Result result = run(workflow, """
                DB_PASSWORD=x
                JWT_SECRET=y
                SYSACAD_API_KEY=
                """);

        assertThat(result.exitCode()).as(result.output()).isEqualTo(1);
        assertThat(result.output()).contains("Falta en el .env del server: TRUSTED_PROXIES");
    }

    @ParameterizedTest(name = "{0}: TRUSTED_PROXIES vacia el chequeo falla")
    @ValueSource(strings = {"cd-dev.yml", "cd-test.yml"})
    @DisplayName("con TRUSTED_PROXIES vacía termina con rc 1 y avisa que está vacía")
    void failsWhenTrustedProxiesIsEmpty(String workflow) throws Exception {
        Result result = run(workflow, """
                DB_PASSWORD=x
                JWT_SECRET=y
                SYSACAD_API_KEY=
                TRUSTED_PROXIES=
                """);

        assertThat(result.exitCode()).as(result.output()).isEqualTo(1);
        assertThat(result.output()).contains("Definida pero vacia en el .env del server: TRUSTED_PROXIES");
    }

    @ParameterizedTest(name = "{0}: el resto de las variables se sigue exigiendo")
    @ValueSource(strings = {"cd-dev.yml", "cd-test.yml"})
    @DisplayName("sin otra variable requerida sigue fallando aunque TRUSTED_PROXIES esté")
    void stillRequiresTheOtherVariables(String workflow) throws Exception {
        Result result = run(workflow, """
                JWT_SECRET=y
                SYSACAD_API_KEY=
                TRUSTED_PROXIES=10.0.0.0/8
                """);

        assertThat(result.exitCode()).as(result.output()).isEqualTo(1);
        assertThat(result.output()).contains("Falta en el .env del server: DB_PASSWORD");
    }

    private Result run(String workflow, String envFile) throws IOException, InterruptedException {
        String bash = findBash();
        assumeTrue(bash != null, "No hay bash disponible: se saltea el chequeo del workflow");

        String script = extractStep(Files.readAllLines(WORKFLOWS.resolve(workflow), StandardCharsets.UTF_8));
        assertThat(script).as("el paso " + STEP_NAME + " en " + workflow).contains("TRUSTED_PROXIES");

        Files.writeString(tempDir.resolve("check.sh"), script, StandardCharsets.UTF_8);
        Files.writeString(tempDir.resolve("docker-compose.deploy.yml"), COMPOSE_TEMPLATE, StandardCharsets.UTF_8);
        Files.writeString(tempDir.resolve(".env"), envFile, StandardCharsets.UTF_8);

        ProcessBuilder builder = new ProcessBuilder(bash, "check.sh")
                .directory(tempDir.toFile())
                .redirectErrorStream(true);
        builder.environment().put("ENV_FILE", ".env");
        Process process = builder.start();
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        assertThat(process.waitFor(30, TimeUnit.SECONDS)).as("el script termina en 30 s").isTrue();
        return new Result(process.exitValue(), output);
    }

    /** Devuelve el cuerpo del {@code run: |} del paso, sin la sangría común. */
    static String extractStep(List<String> lines) {
        int step = -1;
        for (int i = 0; i < lines.size(); i++) {
            if (lines.get(i).strip().equals("- name: " + STEP_NAME)) {
                assertThat(step).as("el paso aparece una sola vez").isEqualTo(-1);
                step = i;
            }
        }
        assertThat(step).as("el workflow tiene el paso " + STEP_NAME).isNotNegative();
        int run = step + 1;
        while (!lines.get(run).strip().equals("run: |")) {
            run++;
        }
        int runIndent = indent(lines.get(run));
        List<String> body = new ArrayList<>();
        for (int i = run + 1; i < lines.size(); i++) {
            String line = lines.get(i);
            if (!line.isBlank() && indent(line) <= runIndent) {
                break;
            }
            body.add(line);
        }
        int bodyIndent = indent(body.getFirst());
        StringBuilder script = new StringBuilder();
        for (String line : body) {
            script.append(line.length() >= bodyIndent ? line.substring(bodyIndent) : line.strip()).append('\n');
        }
        return script.toString();
    }

    private static int indent(String line) {
        int n = 0;
        while (n < line.length() && line.charAt(n) == ' ') {
            n++;
        }
        return n;
    }

    private static String findBash() {
        for (String candidate : BASH_CANDIDATES) {
            try {
                Process probe = new ProcessBuilder(candidate, "-c", "echo ok").redirectErrorStream(true).start();
                String out = new String(probe.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
                if (probe.waitFor(10, TimeUnit.SECONDS) && probe.exitValue() == 0 && out.equals("ok")) {
                    return candidate;
                }
            } catch (IOException | InterruptedException e) {
                // el candidato no existe o no responde: se prueba el siguiente
            }
        }
        return null;
    }

    private record Result(int exitCode, String output) {
    }
}
