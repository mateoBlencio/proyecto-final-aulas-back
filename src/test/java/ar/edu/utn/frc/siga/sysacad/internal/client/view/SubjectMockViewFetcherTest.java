package ar.edu.utn.frc.siga.sysacad.internal.client.view;

import ar.edu.utn.frc.siga.sysacad.internal.client.dto.RawSubject;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("SubjectMockViewFetcher")
class SubjectMockViewFetcherTest {

    private final SubjectMockViewFetcher fetcher =
            new SubjectMockViewFetcher(new ClassPathResource("sysacad/mock/materias.json"), new ObjectMapper());

    @Test
    @DisplayName("fetch: deserializa el fixture con las claves en español de SysAcad")
    void fetchDeserializesFixture() {
        List<RawSubject> rows = fetcher.fetch();

        assertThat(rows).isNotEmpty();
        assertThat(rows).allSatisfy(row -> {
            assertThat(row.especialid()).isNotNull();
            assertThat(row.plan()).isNotNull();
            assertThat(row.materia()).isNotNull();
            assertThat(row.materiaNombre()).isNotBlank();
            // especialidadNombre no se valida: RawSubject lo conserva por fidelidad con la vista real
            // pero SysacadCatalogMapper.toSubject no lo propaga (el nombre llega por la vista de
            // Especialidades), y el mock lo deja null en las materias sin dato.
            if (row.materiaDictado() != null) {
                assertThat(row.materiaDictado()).isIn(Set.of("A", "C"));
            }
        });
    }
}
