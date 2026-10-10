package ar.edu.utn.frc.siga.sysacad.internal.client.view;

import ar.edu.utn.frc.siga.sysacad.internal.client.SysacadClient;
import ar.edu.utn.frc.siga.sysacad.internal.client.ViewQuery;
import ar.edu.utn.frc.siga.sysacad.internal.client.dto.RawHoliday;
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
@DisplayName("HolidayViewFetcher")
class HolidayViewFetcherTest {

    @Mock
    private SysacadClient client;

    @Test
    @DisplayName("fetch: queries Feriados without filters, order or limit")
    void fetchQueriesAllRows() {
        RawHoliday row = new RawHoliday("20261208", "Inmaculada Concepción");
        when(client.fetchRows(eq("Feriados"), eq(ViewQuery.none()), any()))
                .thenReturn(List.of(row));

        HolidayViewFetcher fetcher = new HolidayViewFetcher(client);
        List<RawHoliday> rows = fetcher.fetch();

        assertThat(rows).containsExactly(row);
        verify(client).fetchRows(eq("Feriados"), eq(ViewQuery.none()), any());
    }
}
