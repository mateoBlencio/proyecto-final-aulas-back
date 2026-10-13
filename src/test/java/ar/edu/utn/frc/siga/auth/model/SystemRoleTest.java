package ar.edu.utn.frc.siga.auth.model;

import static org.assertj.core.api.Assertions.assertThat;

import ar.edu.utn.frc.siga.common.security.Permission;
import ar.edu.utn.frc.siga.common.security.ScopeType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("SystemRole - dashboard permissions")
class SystemRoleTest {

    @Test
    @DisplayName("DASHBOARD_READ is granted to SUBSECRETARIA, AUXILIAR_AULICO and CONSULTA")
    void dashboardReadIsGrantedToEveryRole() {
        for (SystemRole role : SystemRole.values()) {
            assertThat(role.permissions()).as(role.name()).contains(Permission.DASHBOARD_READ);
        }
    }

    @Test
    @DisplayName("DASHBOARD_SNAPSHOT_REGENERATE is granted to SUBSECRETARIA only")
    void snapshotRegenerationIsSubsecretariaOnly() {
        assertThat(SystemRole.SUBSECRETARIA.permissions()).contains(Permission.DASHBOARD_SNAPSHOT_REGENERATE);
        assertThat(SystemRole.AUXILIAR_AULICO.permissions()).doesNotContain(Permission.DASHBOARD_SNAPSHOT_REGENERATE);
        assertThat(SystemRole.CONSULTA.permissions()).doesNotContain(Permission.DASHBOARD_SNAPSHOT_REGENERATE);
    }

    @Test
    @DisplayName("DASHBOARD_READ is building-scoped and DASHBOARD_SNAPSHOT_REGENERATE is global")
    void dashboardPermissionScopes() {
        assertThat(Permission.DASHBOARD_READ.scopeType()).isEqualTo(ScopeType.BUILDING);
        assertThat(Permission.DASHBOARD_SNAPSHOT_REGENERATE.scopeType()).isEqualTo(ScopeType.GLOBAL);
    }

    @Test
    @DisplayName("ALLOCATION_HISTORY_READ is granted to SUBSECRETARIA and AUXILIAR_AULICO but not to CONSULTA")
    void allocationHistoryReadIsGrantedToStaffThatOperatesAllocations() {
        assertThat(SystemRole.SUBSECRETARIA.permissions()).contains(Permission.ALLOCATION_HISTORY_READ);
        assertThat(SystemRole.AUXILIAR_AULICO.permissions()).contains(Permission.ALLOCATION_HISTORY_READ);
        assertThat(SystemRole.CONSULTA.permissions()).doesNotContain(Permission.ALLOCATION_HISTORY_READ);
    }
}
