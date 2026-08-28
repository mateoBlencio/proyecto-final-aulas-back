package ar.edu.utn.frc.siga.sysacad.internal.client.view;

import ar.edu.utn.frc.siga.sysacad.internal.client.SysacadClient;
import ar.edu.utn.frc.siga.sysacad.internal.client.dto.RawSchedule;
import ar.edu.utn.frc.siga.sysacad.internal.client.dto.ViewResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "siga.sysacad", name = "enabled", havingValue = "true")
public class ScheduleViewFetcher {

    private static final String VIEW = "HorariosComisionesCupos";
    private static final String SORT_COLUMN = "Curso";
    private static final ParameterizedTypeReference<ViewResponse<RawSchedule>> RESPONSE_TYPE =
            new ParameterizedTypeReference<>() {};

    private final SysacadClient client;

    public List<RawSchedule> fetch() {
        return client.fetchRowsSpanning(VIEW, SORT_COLUMN, null, RESPONSE_TYPE);
    }
}
