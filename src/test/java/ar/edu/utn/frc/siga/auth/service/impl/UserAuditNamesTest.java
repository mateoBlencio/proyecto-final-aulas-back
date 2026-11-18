package ar.edu.utn.frc.siga.auth.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ar.edu.utn.frc.siga.auth.model.User;
import ar.edu.utn.frc.siga.auth.repository.UserRepository;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("UserAuditNames")
class UserAuditNamesTest {

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private UserAuditNames userAuditNames;

    private static User user(String email, String firstName, String lastName) {
        return User.builder().email(email).firstName(firstName).lastName(lastName).build();
    }

    @Test
    @DisplayName("consulta con emails normalizados y devuelve el nombre bajo la clave original de la revisión")
    @SuppressWarnings("unchecked")
    void fullNamesKeepsOriginalKey() {
        when(userRepository.findByEmailIn(any()))
                .thenReturn(List.of(user("jperez@sistemas.frc.utn.edu.ar", "Juan", "Pérez")));

        Map<String, String> names = userAuditNames.fullNames(List.of("JPerez@Sistemas.FRC.utn.edu.ar"));

        assertThat(names).containsExactly(Map.entry("JPerez@Sistemas.FRC.utn.edu.ar", "Juan Pérez"));
        ArgumentCaptor<Collection<String>> captor = ArgumentCaptor.forClass(Collection.class);
        verify(userRepository).findByEmailIn(captor.capture());
        assertThat(captor.getValue()).containsExactly("jperez@sistemas.frc.utn.edu.ar");
    }

    @Test
    @DisplayName("dos variantes del mismo email reciben el mismo nombre, cada una bajo su clave")
    void fullNamesResolvesEveryVariant() {
        when(userRepository.findByEmailIn(any()))
                .thenReturn(List.of(user("ana@frc.utn.edu.ar", "Ana", "López")));

        Map<String, String> names = userAuditNames.fullNames(List.of("ana@frc.utn.edu.ar", "ANA@frc.utn.edu.ar"));

        assertThat(names).containsOnly(
                Map.entry("ana@frc.utn.edu.ar", "Ana López"), Map.entry("ANA@frc.utn.edu.ar", "Ana López"));
    }

    @Test
    @DisplayName("un email sin usuario o con usuario sin nombre no aparece en el mapa")
    void fullNamesSkipsUnknownAndNameless() {
        when(userRepository.findByEmailIn(any()))
                .thenReturn(List.of(user("sinnombre@frc.utn.edu.ar", null, " ")));

        Map<String, String> names = userAuditNames.fullNames(
                List.of("sinnombre@frc.utn.edu.ar", "nadie@frc.utn.edu.ar"));

        assertThat(names).isEmpty();
    }
}
