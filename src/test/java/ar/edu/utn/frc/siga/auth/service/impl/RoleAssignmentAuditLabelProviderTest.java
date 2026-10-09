package ar.edu.utn.frc.siga.auth.service.impl;

import ar.edu.utn.frc.siga.audit.AuditedRecord;
import ar.edu.utn.frc.siga.auth.model.RoleAssignment;
import ar.edu.utn.frc.siga.auth.repository.UserRepository;
import ar.edu.utn.frc.siga.auth.repository.UserRepository.UserEmail;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("RoleAssignmentAuditLabelProvider")
class RoleAssignmentAuditLabelProviderTest {

    @Mock
    private UserRepository userRepository;

    private RoleAssignmentAuditLabelProvider provider;

    @BeforeEach
    void setUp() {
        provider = new RoleAssignmentAuditLabelProvider(userRepository);
    }

    private record Email(Long id, String email) implements UserEmail {
        @Override
        public Long getId() {
            return id;
        }

        @Override
        public String getEmail() {
            return email;
        }
    }

    private static AuditedRecord record(String id, String role, Long userId) {
        Map<String, Object> values = new HashMap<>();
        values.put("role", role);
        values.put("user", userId);
        return new AuditedRecord(id, values);
    }

    @Test
    @DisplayName("entityType es RoleAssignment")
    void entityType_isRoleAssignment() {
        assertThat(provider.entityType()).isEqualTo(RoleAssignment.class);
    }

    @Test
    @DisplayName("formato '{ROL} de {email}'")
    void labels_roleAndEmail() {
        when(userRepository.findEmailsByIdIn(any())).thenReturn(List.of(new Email(7L, "ana@frc.utn.edu.ar")));

        Map<String, String> labels = provider.labels(List.of(record("1", "SUBSECRETARIA", 7L)));

        assertThat(labels).containsExactly(Map.entry("1", "SUBSECRETARIA de ana@frc.utn.edu.ar"));
    }

    @Test
    @DisplayName("una sola consulta de emails para todos los registros, con los ids de usuario sin repetir")
    @SuppressWarnings("unchecked")
    void labels_severalRecords_oneBatchLookup() {
        when(userRepository.findEmailsByIdIn(any())).thenReturn(
                List.of(new Email(7L, "ana@frc.utn.edu.ar"), new Email(8L, "luis@frc.utn.edu.ar")));

        Map<String, String> labels = provider.labels(List.of(
                record("1", "CONSULTA", 7L), record("2", "SUBSECRETARIA", 7L), record("3", "CONSULTA", 8L)));

        ArgumentCaptor<Collection<Long>> ids = ArgumentCaptor.forClass(Collection.class);
        verify(userRepository, times(1)).findEmailsByIdIn(ids.capture());
        assertThat(ids.getValue()).containsExactlyInAnyOrder(7L, 8L);
        assertThat(labels).containsOnly(
                Map.entry("1", "CONSULTA de ana@frc.utn.edu.ar"),
                Map.entry("2", "SUBSECRETARIA de ana@frc.utn.edu.ar"),
                Map.entry("3", "CONSULTA de luis@frc.utn.edu.ar"));
    }

    @Test
    @DisplayName("si no se encuentra el usuario muestra solo el rol")
    void labels_userNotFound_showsOnlyRole() {
        when(userRepository.findEmailsByIdIn(any())).thenReturn(List.of());

        assertThat(provider.labels(List.of(record("1", "CONSULTA", 7L)))).containsExactly(Map.entry("1", "CONSULTA"));
    }

    @Test
    @DisplayName("sin usuario en el estado muestra solo el rol y no consulta emails")
    void labels_noUserId_showsOnlyRoleWithoutLookup() {
        assertThat(provider.labels(List.of(record("1", "CONSULTA", null)))).containsExactly(Map.entry("1", "CONSULTA"));

        verifyNoInteractions(userRepository);
    }

    @Test
    @DisplayName("sin rol el registro no tiene etiqueta aunque exista el usuario")
    void labels_noRole_hasNoLabel() {
        when(userRepository.findEmailsByIdIn(any())).thenReturn(List.of(new Email(7L, "ana@frc.utn.edu.ar")));

        assertThat(provider.labels(List.of(record("1", null, 7L)))).isEmpty();
    }
}
