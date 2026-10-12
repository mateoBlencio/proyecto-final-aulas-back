package ar.edu.utn.frc.siga.audit;

import ar.edu.utn.frc.siga.AbstractIntegrationTest;
import ar.edu.utn.frc.siga.audit.service.AuditArchiveService;
import ar.edu.utn.frc.siga.auth.model.SystemRole;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.context.request.RequestContextHolder;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("Descarga de la auditoría archivada (integración)")
class AuditArchiveExportIntegrationTest extends AbstractIntegrationTest {

    private static final String CYCLES = "/v1/audit/archive/cycles";
    private static final int ARCHIVED_CYCLE = 2002;

    @Autowired
    private AuditArchiveService archiveService;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private WebApplicationContext webApplicationContext;

    private AuditArchiveFixture fixture;

    @BeforeEach
    void seed() {
        fixture = new AuditArchiveFixture(jdbc);
        fixture.begin();
        fixture.seedPeriods();
        fixture.seedScenario();
    }

    @AfterEach
    void cleanUp() {
        fixture.end();
    }

    private void archive() {
        RequestContextHolder.resetRequestAttributes();
        SecurityContextHolder.clearContext();
        archiveService.archive(AuditArchiveFixture.TODAY);
    }

    private Map<String, List<String>> unzip(byte[] zip) throws IOException {
        Map<String, List<String>> files = new LinkedHashMap<>();
        try (ZipInputStream in = new ZipInputStream(new ByteArrayInputStream(zip))) {
            for (ZipEntry entry = in.getNextEntry(); entry != null; entry = in.getNextEntry()) {
                String content = new String(in.readAllBytes(), StandardCharsets.UTF_8);
                files.put(entry.getName(), content.lines().toList());
            }
        }
        return files;
    }

    @Test
    @DisplayName("sin archivados la lista está vacía y descargar un ciclo da 404")
    void sinArchivados() throws Exception {
        String body = mockMvc.perform(get(CYCLES)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();

        assertThat(objectMapper.readTree(body)).isEmpty();
        mockMvc.perform(get(CYCLES + "/{cycle}/export", ARCHIVED_CYCLE)).andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("tras archivar, la lista trae el ciclo con su rango y la cantidad de filas de cada tabla")
    void listaElCicloArchivado() throws Exception {
        archive();

        JsonNode cycles = objectMapper.readTree(
                mockMvc.perform(get(CYCLES)).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());

        assertThat(cycles).hasSize(1);
        JsonNode cycle = cycles.get(0);
        assertThat(cycle.get("cycle").asInt()).isEqualTo(ARCHIVED_CYCLE);
        assertThat(cycle.get("from").isNull()).isTrue();
        assertThat(cycle.get("to").asText()).isEqualTo(AuditArchiveFixture.CUTOFF.toString());
        assertThat(cycle.get("occurrenceRows").asInt()).isEqualTo(5);
        assertThat(cycle.get("allocationRows").asInt()).isEqualTo(3);
    }

    @Test
    @DisplayName("la descarga es un ZIP con asignaciones.csv y ocurrencias.csv, una fila por revisión archivada")
    void descargaElZip() throws Exception {
        archive();

        var response = mockMvc.perform(get(CYCLES + "/{cycle}/export", ARCHIVED_CYCLE))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "application/zip"))
                .andExpect(header().string("Content-Disposition",
                        "attachment; filename=\"auditoria-archivada-ciclo-2002.zip\""))
                .andReturn().getResponse();

        Map<String, List<String>> files = unzip(response.getContentAsByteArray());
        assertThat(files).containsOnlyKeys("ocurrencias.csv", "asignaciones.csv");
        List<String> allocations = files.get("asignaciones.csv");
        assertThat(allocations).hasSize(1 + 3);
        assertThat(allocations.getFirst()).contains("fecha_revision;usuario;tipo_actor;descripcion;operacion_id")
                .contains("id_asignacion").contains("id_aula");
        assertThat(allocations.get(1)).contains("archive-test").contains("CREATED").contains("MANUAL");
        assertThat(files.get("ocurrencias.csv")).hasSize(1 + 5);
        assertThat(files.get("ocurrencias.csv").get(1)).contains("NEEDS_ROOM");
    }

    @Test
    @DisplayName("un ciclo que no se archivó da 404")
    void cicloInexistente() throws Exception {
        archive();

        mockMvc.perform(get(CYCLES + "/{cycle}/export", 1999)).andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("AUXILIAR_AULICO y CONSULTA reciben 403 en la lista y en la descarga")
    void soloSubsecretaria() throws Exception {
        MockMvc secured = MockMvcBuilders.webAppContextSetup(webApplicationContext).apply(springSecurity()).build();
        for (SystemRole role : List.of(SystemRole.AUXILIAR_AULICO, SystemRole.CONSULTA)) {
            var principal = user(role.name().toLowerCase() + "@frc.utn.edu.ar").authorities(role.permissions().stream()
                    .map(permission -> new SimpleGrantedAuthority("PERM_" + permission.name()))
                    .toList());

            secured.perform(get(CYCLES).with(principal)).andExpect(status().isForbidden());
            secured.perform(get(CYCLES + "/{cycle}/export", ARCHIVED_CYCLE).with(principal))
                    .andExpect(status().isForbidden());
        }
    }
}
