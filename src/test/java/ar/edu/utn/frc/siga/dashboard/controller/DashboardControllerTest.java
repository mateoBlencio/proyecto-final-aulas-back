package ar.edu.utn.frc.siga.dashboard.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

import ar.edu.utn.frc.siga.dashboard.dto.response.DashboardBadgesResponseDto;
import ar.edu.utn.frc.siga.dashboard.service.DashboardQueryService;
import ar.edu.utn.frc.siga.dashboard.service.DashboardSnapshotService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

@DisplayName("DashboardController")
class DashboardControllerTest {

    private final DashboardQueryService queryService = mock(DashboardQueryService.class);
    private final DashboardSnapshotService snapshotService = mock(DashboardSnapshotService.class);
    private final DashboardController controller = new DashboardController(queryService, snapshotService);

    @Test
    @DisplayName("badges responds 204 without a body and does not call the query service")
    void badgesIsDisabled() {
        ResponseEntity<DashboardBadgesResponseDto> response = controller.badges();

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(response.getBody()).isNull();
        verifyNoInteractions(queryService, snapshotService);
    }
}
