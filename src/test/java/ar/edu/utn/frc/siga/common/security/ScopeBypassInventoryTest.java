package ar.edu.utn.frc.siga.common.security;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Inventario cerrado de los puntos donde se saltea el alcance por edificio (pedido explícito de
 * .claude/docs/devolucion-rbac.md §2). Cada {@code SystemScope.run/call} y cada
 * {@code BuildingScope.unrestricted()} en {@code src/main} tiene que estar en estas listas: si
 * aparece uno nuevo, el test falla y obliga a justificarlo acá.
 */
@DisplayName("Inventario de bypass de alcance")
class ScopeBypassInventoryTest {

    private static final Path SRC_MAIN = Path.of("src/main/java");

    private static final Map<String, Integer> EXPECTED_SYSTEM_SCOPE = Map.of(
            "ar/edu/utn/frc/siga/allocation/service/impl/OccurrenceVacatedListener.java", 1,
            "ar/edu/utn/frc/siga/ingest/service/impl/IngestServiceImpl.java", 1,
            "ar/edu/utn/frc/siga/sysacad/internal/sync/AllocationSyncService.java", 1);

    private static final Map<String, Integer> EXPECTED_UNRESTRICTED = Map.of(
            "ar/edu/utn/frc/siga/auth/security/DefaultBuildingScopeResolver.java", 1,
            "ar/edu/utn/frc/siga/auth/security/SecurityUser.java", 2);

    @Test
    @DisplayName("SystemScope.run/call sólo aparece en los caminos internos declarados")
    void systemScopeUsagesAreInventoried() {
        assertThat(scan(Pattern.compile("SystemScope\\.(run|call)\\s*\\(")))
                .isEqualTo(new TreeMap<>(EXPECTED_SYSTEM_SCOPE));
    }

    @Test
    @DisplayName("BuildingScope.unrestricted() sólo se usa donde está declarado (excluye common/security)")
    void unrestrictedUsagesAreInventoried() {
        assertThat(scan(Pattern.compile("BuildingScope\\.unrestricted\\s*\\(\\s*\\)")))
                .isEqualTo(new TreeMap<>(EXPECTED_UNRESTRICTED));
    }

    private static Map<String, Integer> scan(Pattern pattern) {
        Map<String, Integer> found = new TreeMap<>();
        try (Stream<Path> files = Files.walk(SRC_MAIN)) {
            for (Path file : files.filter(f -> f.toString().endsWith(".java")).toList()) {
                String rel = SRC_MAIN.relativize(file).toString().replace('\\', '/');
                // El propio paquete common/security define estos helpers; no cuenta como uso.
                if (rel.startsWith("ar/edu/utn/frc/siga/common/security/")) {
                    continue;
                }
                int count = count(Files.readString(file), pattern);
                if (count > 0) {
                    found.put(rel, count);
                }
            }
        } catch (IOException e) {
            throw new IllegalStateException("No se pudo recorrer " + SRC_MAIN, e);
        }
        return found;
    }

    private static int count(String content, Pattern pattern) {
        Matcher matcher = pattern.matcher(content);
        int count = 0;
        while (matcher.find()) {
            count++;
        }
        return count;
    }

    @Test
    @DisplayName("las listas esperadas no quedaron vacías por un cambio de ruta")
    void expectationsAreNotEmpty() {
        assertThat(List.of(EXPECTED_SYSTEM_SCOPE, EXPECTED_UNRESTRICTED)).allSatisfy(m -> assertThat(m).isNotEmpty());
    }
}
