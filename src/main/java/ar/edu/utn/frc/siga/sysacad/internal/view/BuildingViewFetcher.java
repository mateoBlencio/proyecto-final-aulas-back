package ar.edu.utn.frc.siga.sysacad.internal.view;

import ar.edu.utn.frc.siga.sysacad.internal.SysacadClient;
import ar.edu.utn.frc.siga.sysacad.internal.ViewQuery;
import ar.edu.utn.frc.siga.sysacad.internal.dto.RawBuilding;
import ar.edu.utn.frc.siga.sysacad.internal.dto.ViewResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "siga.sysacad", name = "enabled", havingValue = "true")
public class BuildingViewFetcher {

    private static final String VIEW = "Edificios";
    private static final String SORT_COLUMN = "Edificio";
    private static final ParameterizedTypeReference<ViewResponse<RawBuilding>> RESPONSE_TYPE =
            new ParameterizedTypeReference<>() {};

    private final SysacadClient client;

    public List<RawBuilding> fetch() {
        return client.fetchRows(VIEW, ViewQuery.ascendingBy(SORT_COLUMN, null), RESPONSE_TYPE);
    }
}
