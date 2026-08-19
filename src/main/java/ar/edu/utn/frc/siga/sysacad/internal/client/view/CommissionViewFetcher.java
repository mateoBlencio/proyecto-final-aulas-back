package ar.edu.utn.frc.siga.sysacad.internal.client.view;

import ar.edu.utn.frc.siga.sysacad.internal.client.SysacadClient;
import ar.edu.utn.frc.siga.sysacad.internal.client.ViewQuery;
import ar.edu.utn.frc.siga.sysacad.internal.client.dto.RawCommission;
import ar.edu.utn.frc.siga.sysacad.internal.client.dto.ViewResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "siga.sysacad", name = "enabled", havingValue = "true")
public class CommissionViewFetcher {

    private static final String VIEW = "Comisiones";
    private static final String SORT_COLUMN = "curso";
    private static final ParameterizedTypeReference<ViewResponse<RawCommission>> RESPONSE_TYPE =
            new ParameterizedTypeReference<>() {};

    private final SysacadClient client;

    public List<RawCommission> fetch() {
        return client.fetchRows(VIEW, ViewQuery.ascendingBy(SORT_COLUMN, null), RESPONSE_TYPE);
    }
}
