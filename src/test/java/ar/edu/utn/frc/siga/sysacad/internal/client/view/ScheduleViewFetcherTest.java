package ar.edu.utn.frc.siga.sysacad.internal.client.view;

import ar.edu.utn.frc.siga.sysacad.internal.client.SysacadClient;
import ar.edu.utn.frc.siga.sysacad.internal.client.ViewQuery;
import ar.edu.utn.frc.siga.sysacad.internal.client.dto.RawSchedule;
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
@DisplayName("ScheduleViewFetcher")
class ScheduleViewFetcherTest {

    @Mock
    private SysacadClient client;

    @Test
    @DisplayName("fetch: consulta HorariosComisionesCupos sin filtros, orden ni límite")
    void fetchQueriesAllRows() {
        // Fila de muestra real (sample), tal como la devuelve HorariosComisionesCupos.
        RawSchedule row = new RawSchedule(
                "1H90SR", 90, 999, 24, "Edif. Ing.Possetto",
                2, 0, "A", "A",
                "10:30", "12:45", "10:30-12:45", 135,
                5, "Ingeniería en Sistemas de Información", 2008, 115, "Sistemas de Representación", 0);
        when(client.fetchRows(eq("HorariosComisionesCupos"), eq(ViewQuery.none()), any()))
                .thenReturn(List.of(row));

        ScheduleViewFetcher fetcher = new ScheduleViewFetcher(client);
        List<RawSchedule> rows = fetcher.fetch();

        assertThat(rows).containsExactly(row);
        verify(client).fetchRows(eq("HorariosComisionesCupos"), eq(ViewQuery.none()), any());
    }
}
