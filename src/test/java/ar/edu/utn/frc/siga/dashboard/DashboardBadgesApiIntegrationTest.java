package ar.edu.utn.frc.siga.dashboard;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ar.edu.utn.frc.siga.AbstractIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("Dashboard - GET /v1/dashboard/badges (integración)")
class DashboardBadgesApiIntegrationTest extends AbstractIntegrationTest {

    @Test
    @DisplayName("responds the four counters for an unrestricted user")
    void unrestrictedUserGetsAllCounters() throws Exception {
        mockMvc.perform(get("/v1/dashboard/badges"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.roomRequestsAwaitingAction").isNumber())
                .andExpect(jsonPath("$.roomRequestsExpiringSoon").isNumber())
                .andExpect(jsonPath("$.unallocatedClassesNext7Days").isNumber())
                .andExpect(jsonPath("$.conflictsNext7Days").isNumber());
    }
}
