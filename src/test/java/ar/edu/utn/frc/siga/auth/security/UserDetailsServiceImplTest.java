package ar.edu.utn.frc.siga.auth.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import ar.edu.utn.frc.siga.auth.model.User;
import ar.edu.utn.frc.siga.auth.repository.UserRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

@ExtendWith(MockitoExtension.class)
@DisplayName("UserDetailsServiceImpl")
class UserDetailsServiceImplTest {

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private UserDetailsServiceImpl service;

    @Test
    @DisplayName("loadUserByUsername busca con el email normalizado (sin espacios, en minúsculas)")
    void loadUserByUsernameNormalizesEmail() {
        String normalized = "jperez@sistemas.frc.utn.edu.ar";
        User user = User.builder().id(1L).email(normalized).passwordHash("HASH").enabled(true)
                .roleAssignments(List.of()).build();
        when(userRepository.findByEmailAndEnabledTrue(normalized)).thenReturn(Optional.of(user));

        UserDetails details = service.loadUserByUsername("  JPerez@Sistemas.FRC.utn.edu.ar ");

        assertThat(details.getUsername()).isEqualTo(normalized);
    }

    @Test
    @DisplayName("loadUserByUsername sin usuario habilitado → UsernameNotFoundException")
    void loadUserByUsernameNotFound() {
        when(userRepository.findByEmailAndEnabledTrue("nadie@frc.utn.edu.ar")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.loadUserByUsername("Nadie@FRC.utn.edu.ar"))
                .isInstanceOf(UsernameNotFoundException.class);
    }
}
