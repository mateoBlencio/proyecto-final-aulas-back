package ar.edu.utn.frc.siga.sysacad.internal.client.view;

import ar.edu.utn.frc.siga.sysacad.internal.client.SysacadClient;
import ar.edu.utn.frc.siga.sysacad.internal.client.ViewQuery;
import ar.edu.utn.frc.siga.sysacad.internal.client.dto.RawSpecialty;
import ar.edu.utn.frc.siga.sysacad.internal.client.dto.ViewResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "siga.sysacad", name = "enabled", havingValue = "true")
public class SpecialtyViewFetcher {

    private static final String VIEW = "Especialidades";
    private static final String SORT_COLUMN = "especialid";
    private static final int MAX_ROWS = 300;
    private static final ParameterizedTypeReference<ViewResponse<RawSpecialty>> RESPONSE_TYPE =
            new ParameterizedTypeReference<>() {};

    private final SysacadClient client;

    public List<RawSpecialty> fetch() {
        return client.fetchRows(VIEW, ViewQuery.ascendingBy(SORT_COLUMN, MAX_ROWS), RESPONSE_TYPE);
    }
}
