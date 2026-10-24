package ar.edu.utn.frc.siga.cameracount;

import ar.edu.utn.frc.siga.auth.model.RoleAssignment;
import ar.edu.utn.frc.siga.auth.model.SystemRole;
import ar.edu.utn.frc.siga.auth.model.User;
import ar.edu.utn.frc.siga.auth.security.SecurityUser;
import ar.edu.utn.frc.siga.cameracount.controller.CameraCountController;
import ar.edu.utn.frc.siga.cameracount.dto.request.CameraCountRequestDto;
import ar.edu.utn.frc.siga.common.security.Authorities;
import ar.edu.utn.frc.siga.common.security.Permission;
import ar.edu.utn.frc.siga.common.security.ScopeType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.GrantedAuthority;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("CAMERA_COUNT_RUN: quién tiene el permiso y qué endpoints lo exigen")
class CameraCountPermissionTest {

    private static final String REQUIRED = "hasAuthority('" + Authorities.of(Permission.CAMERA_COUNT_RUN) + "')";

    @ParameterizedTest(name = "{0} con alcance {1} -> tiene PERM_CAMERA_COUNT_RUN: {2}")
    @CsvSource({
            "SUBSECRETARIA, GLOBAL, true",
            "AUXILIAR_AULICO, BUILDING, true",
            "CONSULTA, GLOBAL, false"
    })
    @DisplayName("SUBSECRETARIA y AUXILIAR_AULICO (acotado a un edificio) reciben la authority; CONSULTA no")
    void roleReceivesAuthority(SystemRole role, ScopeType scopeType, boolean expected) {
        Long buildingId = scopeType == ScopeType.BUILDING ? 5L : null;
        User user = User.builder().id(1L).email("u@frc.utn.edu.ar").enabled(true)
                .roleAssignments(List.of(RoleAssignment.builder().role(role).scopeType(scopeType)
                        .scopeId(buildingId).build()))
                .build();

        Set<String> authorities = SecurityUser.fromUser(user).getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.toSet());

        assertThat(authorities.contains(Authorities.of(Permission.CAMERA_COUNT_RUN)))
                .as("authorities de %s: %s", role, authorities)
                .isEqualTo(expected);
    }

    @Test
    @DisplayName("el permiso es GLOBAL: no depende del edificio del auxiliar")
    void permissionIsGlobal() {
        assertThat(Permission.CAMERA_COUNT_RUN.scopeType()).isEqualTo(ScopeType.GLOBAL);
    }

    @Test
    @DisplayName("POST /camera-counts y GET /camera-counts/health exigen exactamente PERM_CAMERA_COUNT_RUN")
    void endpointsRequireCameraCountPermission() throws NoSuchMethodException {
        Method count = CameraCountController.class.getMethod("count", CameraCountRequestDto.class);
        Method health = CameraCountController.class.getMethod("health");

        assertThat(count.getAnnotation(PreAuthorize.class)).isNotNull();
        assertThat(count.getAnnotation(PreAuthorize.class).value()).isEqualTo(REQUIRED);
        assertThat(health.getAnnotation(PreAuthorize.class)).isNotNull();
        assertThat(health.getAnnotation(PreAuthorize.class).value()).isEqualTo(REQUIRED);
    }
}
