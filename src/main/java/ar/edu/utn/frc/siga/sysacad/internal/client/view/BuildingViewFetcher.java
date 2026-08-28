package ar.edu.utn.frc.siga.sysacad.internal.client.view;

import ar.edu.utn.frc.siga.sysacad.internal.client.SysacadClient;
import ar.edu.utn.frc.siga.sysacad.internal.client.dto.RawBuilding;
import ar.edu.utn.frc.siga.sysacad.internal.client.dto.ViewResponse;
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
        return client.fetchRowsSpanning(VIEW, SORT_COLUMN, null, RESPONSE_TYPE);
    }
}
