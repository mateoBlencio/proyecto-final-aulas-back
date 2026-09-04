package ar.edu.utn.frc.siga.auth.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ar.edu.utn.frc.siga.auth.dto.request.AssignRoleRequestDto;
import ar.edu.utn.frc.siga.auth.dto.response.RoleAssignmentDto;
import ar.edu.utn.frc.siga.auth.exception.RoleDomainException;
import ar.edu.utn.frc.siga.auth.mapper.RoleAssignmentComposer;
import ar.edu.utn.frc.siga.auth.model.Role;
import ar.edu.utn.frc.siga.auth.model.RoleAssignment;
import ar.edu.utn.frc.siga.auth.model.User;
import ar.edu.utn.frc.siga.auth.repository.RoleAssignmentRepository;
import ar.edu.utn.frc.siga.auth.repository.RoleRepository;
import ar.edu.utn.frc.siga.auth.repository.UserRepository;
import ar.edu.utn.frc.siga.common.exception.ResourceNotFoundException;
import ar.edu.utn.frc.siga.common.security.ScopeType;
import ar.edu.utn.frc.siga.space.dto.response.BuildingResponseDto;
import ar.edu.utn.frc.siga.space.service.BuildingService;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("RoleAssignmentServiceImpl")
class RoleAssignmentServiceImplTest {

    private static final String OTHER_EMAIL = "admin@frc.utn.edu.ar";

    @Mock
    private UserRepository userRepository;
    @Mock
    private RoleRepository roleRepository;
    @Mock
    private RoleAssignmentRepository roleAssignmentRepository;
    @Mock
    private BuildingService buildingService;
    @Mock
    private RoleAssignmentComposer roleAssignmentComposer;

    private RoleAssignmentServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new RoleAssignmentServiceImpl(userRepository, roleRepository, roleAssignmentRepository,
                buildingService, roleAssignmentComposer);
    }

    private User user(Long id, String email) {
        return User.builder().id(id).email(email).build();
    }

    private Role role(Long id) {
        return Role.builder().id(id).name("AUXILIAR_AULICO").systemRole(true).build();
    }

    @Test
    @DisplayName("assign: alcance BUILDING sin scopeId → RoleDomainException")
    void assignBuildingWithoutScopeIdThrows() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(user(1L, "u@frc.utn.edu.ar")));
        when(roleRepository.findById(2L)).thenReturn(Optional.of(role(2L)));

        AssignRoleRequestDto dto = new AssignRoleRequestDto(2L, ScopeType.BUILDING, null);

        assertThatThrownBy(() -> service.assign(1L, dto, OTHER_EMAIL)).isInstanceOf(RoleDomainException.class);

        verify(roleAssignmentRepository, never()).save(any());
    }

    @Test
    @DisplayName("assign: alcance GLOBAL con scopeId → RoleDomainException")
    void assignGlobalWithScopeIdThrows() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(user(1L, "u@frc.utn.edu.ar")));
        when(roleRepository.findById(2L)).thenReturn(Optional.of(role(2L)));

        AssignRoleRequestDto dto = new AssignRoleRequestDto(2L, ScopeType.GLOBAL, 5L);

        assertThatThrownBy(() -> service.assign(1L, dto, OTHER_EMAIL)).isInstanceOf(RoleDomainException.class);

        verify(roleAssignmentRepository, never()).save(any());
    }

    @Test
    @DisplayName("assign: edificio inexistente → propaga ResourceNotFoundException")
    void assignNonexistentBuildingPropagates404() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(user(1L, "u@frc.utn.edu.ar")));
        when(roleRepository.findById(2L)).thenReturn(Optional.of(role(2L)));
        when(buildingService.findById(99L)).thenThrow(ResourceNotFoundException.of("Building", 99L));

        AssignRoleRequestDto dto = new AssignRoleRequestDto(2L, ScopeType.BUILDING, 99L);

        assertThatThrownBy(() -> service.assign(1L, dto, OTHER_EMAIL)).isInstanceOf(ResourceNotFoundException.class);

        verify(roleAssignmentRepository, never()).save(any());
    }

    @Test
    @DisplayName("assign: asignación duplicada (mismo rol y alcance) → RoleDomainException")
    void assignDuplicateThrows() {
        User u = user(1L, "u@frc.utn.edu.ar");
        Role r = role(2L);
        when(userRepository.findById(1L)).thenReturn(Optional.of(u));
        when(roleRepository.findById(2L)).thenReturn(Optional.of(r));
        when(buildingService.findById(5L)).thenReturn(new BuildingResponseDto(5L, "Edificio 5", true));
        RoleAssignment existing = RoleAssignment.builder().id(10L).user(u).role(r)
                .scopeType(ScopeType.BUILDING).scopeId(5L).build();
        when(roleAssignmentRepository.findAllByUserId(1L)).thenReturn(List.of(existing));

        AssignRoleRequestDto dto = new AssignRoleRequestDto(2L, ScopeType.BUILDING, 5L);

        assertThatThrownBy(() -> service.assign(1L, dto, OTHER_EMAIL)).isInstanceOf(RoleDomainException.class);

        verify(roleAssignmentRepository, never()).save(any());
    }

    @Test
    @DisplayName("assign: alcance válido y sin duplicados → guarda y compone la respuesta")
    void assignHappyPath() {
        User u = user(1L, "u@frc.utn.edu.ar");
        Role r = role(2L);
        when(userRepository.findById(1L)).thenReturn(Optional.of(u));
        when(roleRepository.findById(2L)).thenReturn(Optional.of(r));
        when(buildingService.findById(5L)).thenReturn(new BuildingResponseDto(5L, "Edificio 5", true));
        when(roleAssignmentRepository.findAllByUserId(1L)).thenReturn(List.of());
        when(roleAssignmentRepository.save(any(RoleAssignment.class))).thenAnswer(inv -> {
            RoleAssignment a = inv.getArgument(0);
            a.setId(50L);
            return a;
        });
        RoleAssignmentDto expected = new RoleAssignmentDto(50L, "AUXILIAR_AULICO", ScopeType.BUILDING, 5L, "Edificio 5");
        when(roleAssignmentComposer.compose(any())).thenReturn(expected);

        RoleAssignmentDto result = service.assign(1L, new AssignRoleRequestDto(2L, ScopeType.BUILDING, 5L), OTHER_EMAIL);

        assertThat(result).isEqualTo(expected);
        verify(roleAssignmentRepository).save(any(RoleAssignment.class));
    }

    @Test
    @DisplayName("assign: usuario inexistente → ResourceNotFoundException")
    void assignUserNotFoundThrows() {
        when(userRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.assign(99L, new AssignRoleRequestDto(2L, ScopeType.GLOBAL, null), OTHER_EMAIL))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("assign: rol inexistente → ResourceNotFoundException")
    void assignRoleNotFoundThrows() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(user(1L, "u@frc.utn.edu.ar")));
        when(roleRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.assign(1L, new AssignRoleRequestDto(99L, ScopeType.GLOBAL, null), OTHER_EMAIL))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("revoke: asignación de otro usuario → ResourceNotFoundException, no borra")
    void revokeOtherUsersAssignmentThrows() {
        User owner = user(2L, "otro@frc.utn.edu.ar");
        RoleAssignment assignment = RoleAssignment.builder().id(10L).user(owner).role(role(3L))
                .scopeType(ScopeType.GLOBAL).build();
        when(roleAssignmentRepository.findById(10L)).thenReturn(Optional.of(assignment));

        assertThatThrownBy(() -> service.revoke(1L, 10L, OTHER_EMAIL)).isInstanceOf(ResourceNotFoundException.class);

        verify(roleAssignmentRepository, never()).delete(any());
    }

    @Test
    @DisplayName("revoke: auto-revocación → RoleDomainException, no borra")
    void revokeOwnAssignmentThrows() {
        User self = user(1L, OTHER_EMAIL);
        RoleAssignment assignment = RoleAssignment.builder().id(10L).user(self).role(role(3L))
                .scopeType(ScopeType.GLOBAL).build();
        when(roleAssignmentRepository.findById(10L)).thenReturn(Optional.of(assignment));

        assertThatThrownBy(() -> service.revoke(1L, 10L, OTHER_EMAIL)).isInstanceOf(RoleDomainException.class);

        verify(roleAssignmentRepository, never()).delete(any());
    }

    @Test
    @DisplayName("revoke: asignación inexistente → ResourceNotFoundException")
    void revokeNotFoundThrows() {
        when(roleAssignmentRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.revoke(1L, 99L, OTHER_EMAIL)).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("revoke: propia y del usuario correcto → borra")
    void revokeHappyPathDeletes() {
        User owner = user(1L, "u@frc.utn.edu.ar");
        RoleAssignment assignment = RoleAssignment.builder().id(10L).user(owner).role(role(3L))
                .scopeType(ScopeType.GLOBAL).build();
        when(roleAssignmentRepository.findById(10L)).thenReturn(Optional.of(assignment));

        service.revoke(1L, 10L, OTHER_EMAIL);

        verify(roleAssignmentRepository).delete(assignment);
    }
}
