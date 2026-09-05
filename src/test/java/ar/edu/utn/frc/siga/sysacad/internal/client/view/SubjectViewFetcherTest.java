package ar.edu.utn.frc.siga.sysacad.internal.client.view;

import ar.edu.utn.frc.siga.sysacad.internal.client.SysacadClient;
import ar.edu.utn.frc.siga.sysacad.internal.client.ViewQuery;
import ar.edu.utn.frc.siga.sysacad.internal.client.dto.RawSubject;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("SubjectViewFetcher")
class SubjectViewFetcherTest {

    @Mock
    private SysacadClient client;

    @Test
    @DisplayName("fetch: consulta Materias sin filtros, orden ni límite")
    void fetchQueriesAllRows() {
        RawSubject row = new RawSubject(17, 94, 519, "Análisis Matemático I");
        when(client.fetchRows(eq("Materias"), eq(ViewQuery.none()), any()))
                .thenReturn(List.of(row));

        SubjectViewFetcher fetcher = new SubjectViewFetcher(client);
        List<RawSubject> rows = fetcher.fetch();

        assertThat(rows).containsExactly(row);
        verify(client).fetchRows(eq("Materias"), eq(ViewQuery.none()), any());
    }
}
