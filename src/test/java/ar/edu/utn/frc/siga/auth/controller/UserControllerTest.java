package ar.edu.utn.frc.siga.auth.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ar.edu.utn.frc.siga.auth.dto.request.UpdateUserEnabledRequestDto;
import ar.edu.utn.frc.siga.auth.dto.response.UserResponseDto;
import ar.edu.utn.frc.siga.auth.security.SecurityUser;
import ar.edu.utn.frc.siga.auth.service.RoleAssignmentService;
import ar.edu.utn.frc.siga.auth.service.UserService;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

@ExtendWith(MockitoExtension.class)
@DisplayName("UserController")
class UserControllerTest {

    @Mock
    private UserService userService;
    @Mock
    private RoleAssignmentService roleAssignmentService;

    @Test
    @DisplayName("setEnabled pasa al servicio el email del usuario autenticado")
    void setEnabledPassesPrincipalEmail() {
        UserController controller = new UserController(userService, roleAssignmentService);
        SecurityUser principal = new SecurityUser(1L, "admin@frc.utn.edu.ar", "hash", true, Map.of());
        UserResponseDto dto = new UserResponseDto(7L, "u@frc.utn.edu.ar", "Nombre", "Apellido", null);
        when(userService.setEnabled(7L, false, "admin@frc.utn.edu.ar")).thenReturn(dto);

        ResponseEntity<UserResponseDto> response =
                controller.setEnabled(7L, new UpdateUserEnabledRequestDto(false), principal);

        verify(userService).setEnabled(7L, false, "admin@frc.utn.edu.ar");
        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody()).isSameAs(dto);
    }
}
