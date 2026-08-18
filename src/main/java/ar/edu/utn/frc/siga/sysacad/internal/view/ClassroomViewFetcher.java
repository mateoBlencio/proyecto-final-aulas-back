package ar.edu.utn.frc.siga.sysacad.internal.view;

import ar.edu.utn.frc.siga.sysacad.internal.SysacadClient;
import ar.edu.utn.frc.siga.sysacad.internal.ViewQuery;
import ar.edu.utn.frc.siga.sysacad.internal.dto.RawClassroom;
import ar.edu.utn.frc.siga.sysacad.internal.dto.ViewResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "siga.sysacad", name = "enabled", havingValue = "true")
public class ClassroomViewFetcher {

    private static final String VIEW = "Aulas";
    private static final String SORT_COLUMN = "Aula";
    private static final int MAX_ROWS = 500;
    private static final ParameterizedTypeReference<ViewResponse<RawClassroom>> RESPONSE_TYPE =
            new ParameterizedTypeReference<>() {};

    private final SysacadClient client;

    public List<RawClassroom> fetch() {
        return client.fetchRows(VIEW, ViewQuery.ascendingBy(SORT_COLUMN, MAX_ROWS), RESPONSE_TYPE);
    }
}
