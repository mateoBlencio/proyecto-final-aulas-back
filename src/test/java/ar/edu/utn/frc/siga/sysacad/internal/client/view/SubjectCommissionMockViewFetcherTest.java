package ar.edu.utn.frc.siga.sysacad.internal.client.view;

import ar.edu.utn.frc.siga.sysacad.internal.client.dto.RawSubjectCommission;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("SubjectCommissionMockViewFetcher")
class SubjectCommissionMockViewFetcherTest {

    private final SubjectCommissionMockViewFetcher fetcher = new SubjectCommissionMockViewFetcher(
            new ClassPathResource("sysacad/mock/materia_comision.json"), new ObjectMapper());

    @Test
    @DisplayName("fetch: deserializa el fixture con las claves en español de SysAcad")
    void fetchDeserializesFixture() {
        List<RawSubjectCommission> rows = fetcher.fetch();

        assertThat(rows).isNotEmpty();
        assertThat(rows).allSatisfy(row -> {
            assertThat(row.curso()).isNotBlank();
            assertThat(row.materia()).isNotNull();
            assertThat(row.inscriptos()).isNotNull();
            assertThat(row.comisionDictado()).isIn(Set.of("A", "1", "2"));
        });
    }
}
