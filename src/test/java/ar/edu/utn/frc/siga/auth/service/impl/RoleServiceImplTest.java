package ar.edu.utn.frc.siga.auth.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ar.edu.utn.frc.siga.auth.dto.request.CreateRoleRequestDto;
import ar.edu.utn.frc.siga.auth.dto.response.PermissionResponseDto;
import ar.edu.utn.frc.siga.auth.dto.response.RoleResponseDto;
import ar.edu.utn.frc.siga.auth.exception.RoleDomainException;
import ar.edu.utn.frc.siga.auth.model.Role;
import ar.edu.utn.frc.siga.auth.repository.RoleAssignmentRepository;
import ar.edu.utn.frc.siga.auth.repository.RoleRepository;
import ar.edu.utn.frc.siga.common.exception.ResourceNotFoundException;
import ar.edu.utn.frc.siga.common.security.Permission;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("RoleServiceImpl")
class RoleServiceImplTest {

    @Mock
    private RoleRepository roleRepository;
    @Mock
    private RoleAssignmentRepository roleAssignmentRepository;

    private RoleServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new RoleServiceImpl(roleRepository, roleAssignmentRepository);
    }

    private Role role(Long id, String name, boolean systemRole) {
        return Role.builder().id(id).name(name).systemRole(systemRole)
                .permissions(Set.of(Permission.BUILDING_READ)).build();
    }

    @Test
    @DisplayName("findAll: mapea todos los roles")
    void findAllMapsRoles() {
        when(roleRepository.findAll()).thenReturn(List.of(role(1L, "CONSULTA", true)));

        List<RoleResponseDto> result = service.findAll();

        assertThat(result).hasSize(1);
        assertThat(result.getFirst().name()).isEqualTo("CONSULTA");
        assertThat(result.getFirst().systemRole()).isTrue();
    }

    @Test
    @DisplayName("findAllPermissions: devuelve el catálogo completo de Permission con su scopeType")
    void findAllPermissionsReturnsFullCatalog() {
        List<PermissionResponseDto> result = service.findAllPermissions();

        assertThat(result).hasSize(Permission.values().length);
        assertThat(result).allSatisfy(dto -> assertThat(dto.scopeType()).isEqualTo(dto.permission().scopeType()));
    }

    @Test
    @DisplayName("create: nombre nuevo → crea el rol como no-sistema")
    void createHappyPath() {
        when(roleRepository.existsByName("CUSTOM")).thenReturn(false);
        when(roleRepository.save(any(Role.class))).thenAnswer(inv -> inv.getArgument(0));

        RoleResponseDto result = service.create(new CreateRoleRequestDto("CUSTOM", Set.of(Permission.CLASSROOM_READ)));

        assertThat(result.name()).isEqualTo("CUSTOM");
        assertThat(result.systemRole()).isFalse();
    }

    @Test
    @DisplayName("create: nombre duplicado → RoleDomainException, no guarda")
    void createDuplicateNameThrows() {
        when(roleRepository.existsByName("SUBSECRETARIA")).thenReturn(true);

        assertThatThrownBy(() -> service.create(new CreateRoleRequestDto("SUBSECRETARIA", Set.of(Permission.USER_READ))))
                .isInstanceOf(RoleDomainException.class);

        verify(roleRepository, never()).save(any());
    }

    @Test
    @DisplayName("updatePermissions: reemplaza el set de permisos, incluso de un rol de sistema")
    void updatePermissionsReplacesSet() {
        Role systemRole = role(1L, "AUXILIAR_AULICO", true);
        when(roleRepository.findById(1L)).thenReturn(Optional.of(systemRole));
        when(roleRepository.save(any(Role.class))).thenAnswer(inv -> inv.getArgument(0));

        RoleResponseDto result = service.updatePermissions(1L, Set.of(Permission.EVENT_READ));

        assertThat(result.permissions()).containsExactly(Permission.EVENT_READ);
    }

    @Test
    @DisplayName("delete: rol de sistema → RoleDomainException, no borra")
    void deleteSystemRoleThrows() {
        when(roleRepository.findById(1L)).thenReturn(Optional.of(role(1L, "SUBSECRETARIA", true)));

        assertThatThrownBy(() -> service.delete(1L)).isInstanceOf(RoleDomainException.class);

        verify(roleRepository, never()).delete(any());
    }

    @Test
    @DisplayName("delete: rol en uso → RoleDomainException, no borra")
    void deleteRoleInUseThrows() {
        when(roleRepository.findById(2L)).thenReturn(Optional.of(role(2L, "CUSTOM", false)));
        when(roleAssignmentRepository.existsByRoleId(2L)).thenReturn(true);

        assertThatThrownBy(() -> service.delete(2L)).isInstanceOf(RoleDomainException.class);

        verify(roleRepository, never()).delete(any());
    }

    @Test
    @DisplayName("delete: rol no-sistema y sin uso → se borra")
    void deleteUnusedCustomRoleDeletes() {
        Role custom = role(2L, "CUSTOM", false);
        when(roleRepository.findById(2L)).thenReturn(Optional.of(custom));
        when(roleAssignmentRepository.existsByRoleId(2L)).thenReturn(false);

        service.delete(2L);

        verify(roleRepository).delete(custom);
    }

    @Test
    @DisplayName("delete: rol inexistente → ResourceNotFoundException")
    void deleteNotFoundThrows() {
        when(roleRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.delete(99L)).isInstanceOf(ResourceNotFoundException.class);
    }
}
