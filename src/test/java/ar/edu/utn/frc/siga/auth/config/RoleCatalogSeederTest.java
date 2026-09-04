package ar.edu.utn.frc.siga.auth.config;

import ar.edu.utn.frc.siga.auth.model.Role;
import ar.edu.utn.frc.siga.auth.model.SystemRole;
import ar.edu.utn.frc.siga.auth.repository.RoleRepository;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("RoleCatalogSeeder")
class RoleCatalogSeederTest {

    @Mock
    private RoleRepository repository;

    private RoleCatalogSeeder seeder;

    @BeforeEach
    void setUp() {
        seeder = new RoleCatalogSeeder(repository);
    }

    @Test
    @DisplayName("Con la base vacía siembra un rol de sistema por cada SystemRole con sus permisos por defecto")
    void seedsAllSystemRolesWhenDatabaseEmpty() {
        when(repository.existsByName(any())).thenReturn(false);

        seeder.run(null);

        ArgumentCaptor<Role> captor = ArgumentCaptor.forClass(Role.class);
        verify(repository, times(SystemRole.values().length)).save(captor.capture());

        List<Role> saved = captor.getAllValues();
        assertThat(saved).hasSize(SystemRole.values().length);
        for (SystemRole systemRole : SystemRole.values()) {
            assertThat(saved).anyMatch(role -> role.getName().equals(systemRole.name())
                    && role.isSystemRole()
                    && role.getPermissions().equals(systemRole.defaultPermissions()));
        }
    }

    @Test
    @DisplayName("Es idempotente: sólo siembra los roles de sistema que faltan")
    void seedsOnlyMissingSystemRoles() {
        when(repository.existsByName(any())).thenReturn(false);
        when(repository.existsByName(SystemRole.SUBSECRETARIA.name())).thenReturn(true);

        seeder.run(null);

        verify(repository, times(SystemRole.values().length - 1)).save(any(Role.class));
        ArgumentCaptor<Role> captor = ArgumentCaptor.forClass(Role.class);
        verify(repository, times(SystemRole.values().length - 1)).save(captor.capture());
        assertThat(captor.getAllValues()).noneMatch(role -> role.getName().equals(SystemRole.SUBSECRETARIA.name()));
    }

    @Test
    @DisplayName("Con la base ya sembrada no inserta nada")
    void seedsNothingWhenAllPresent() {
        when(repository.existsByName(any())).thenReturn(true);

        seeder.run(null);

        verify(repository, never()).save(any(Role.class));
    }
}
