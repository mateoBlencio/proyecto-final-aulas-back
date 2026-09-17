package ar.edu.utn.frc.siga.auth.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ar.edu.utn.frc.siga.auth.config.AuthDomainProperties;
import ar.edu.utn.frc.siga.auth.dto.request.CreateUserRequestDto;
import ar.edu.utn.frc.siga.auth.dto.response.UserResponseDto;
import ar.edu.utn.frc.siga.auth.exception.UserDomainException;
import ar.edu.utn.frc.siga.auth.mapper.RoleAssignmentComposer;
import ar.edu.utn.frc.siga.auth.mapper.UserMapper;
import ar.edu.utn.frc.siga.auth.model.User;
import ar.edu.utn.frc.siga.auth.repository.UserRepository;
import ar.edu.utn.frc.siga.auth.service.RefreshTokenService;
import ar.edu.utn.frc.siga.auth.service.RoleAssignmentService;
import ar.edu.utn.frc.siga.common.exception.ResourceNotFoundException;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
@DisplayName("UserServiceImpl")
class UserServiceImplTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private RefreshTokenService refreshTokenService;
    @Mock
    private UserMapper userMapper;
    @Mock
    private RoleAssignmentComposer roleAssignmentComposer;
    @Mock
    private RoleAssignmentService roleAssignmentService;
    @Mock
    private AuthDomainProperties authDomainProperties;

    private UserServiceImpl service;

    private static final String CREATOR_EMAIL = "admin@frc.utn.edu.ar";

    @BeforeEach
    void setUp() {
        service = new UserServiceImpl(userRepository, passwordEncoder, refreshTokenService,
                userMapper, roleAssignmentComposer, roleAssignmentService, authDomainProperties);
        lenient().when(roleAssignmentComposer.composeAll(any())).thenReturn(List.of());
    }

    private CreateUserRequestDto createDto(String email) {
        return new CreateUserRequestDto(email, "supersegura", "Nombre", "Apellido", null);
    }

    private User existing(Long id, String email) {
        User user = new User();
        user.setId(id);
        user.setEmail(email);
        user.setEnabled(true);
        user.setFirstName("Nombre");
        user.setLastName("Apellido");
        return user;
    }

    @Test
    @DisplayName("create: email institucional nuevo → hashea password, guarda con nombre/apellido y devuelve DTO")
    void createHappyPath() {
        when(authDomainProperties.isAllowedEmail(any())).thenReturn(true);
        when(userRepository.existsByEmail("nuevo@frc.utn.edu.ar")).thenReturn(false);
        when(passwordEncoder.encode("supersegura")).thenReturn("HASH");
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));
        when(userMapper.toDto(any(User.class)))
                .thenReturn(new UserResponseDto(1L, "nuevo@frc.utn.edu.ar", "Nombre", "Apellido", List.of()));

        UserResponseDto result = service.create(createDto("nuevo@frc.utn.edu.ar"), CREATOR_EMAIL);

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        User saved = captor.getValue();
        assertThat(saved.getPasswordHash()).isEqualTo("HASH");
        assertThat(saved.getEnabled()).isTrue();
        assertThat(saved.getFirstName()).isEqualTo("Nombre");
        assertThat(saved.getLastName()).isEqualTo("Apellido");
        assertThat(result.firstName()).isEqualTo("Nombre");
    }

    @Test
    @DisplayName("create: email ya existente → UserDomainException y no guarda")
    void createDuplicateEmailThrows() {
        when(authDomainProperties.isAllowedEmail(any())).thenReturn(true);
        when(userRepository.existsByEmail("dup@frc.utn.edu.ar")).thenReturn(true);

        assertThatThrownBy(() -> service.create(createDto("dup@frc.utn.edu.ar"), CREATOR_EMAIL))
                .isInstanceOf(UserDomainException.class);

        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("create: dominio no institucional → UserDomainException y no consulta existencia")
    void createNonInstitutionalDomainThrows() {
        assertThatThrownBy(() -> service.create(createDto("ajeno@gmail.com"), CREATOR_EMAIL))
                .isInstanceOf(UserDomainException.class);

        verify(userRepository, never()).existsByEmail(any());
        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("setEnabled(false): inhabilita y revoca los refresh tokens")
    void disableRevokesTokens() {
        User user = existing(7L, "u@frc.utn.edu.ar");
        when(userRepository.findById(7L)).thenReturn(Optional.of(user));
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(userMapper.toDto(any(User.class)))
                .thenReturn(new UserResponseDto(7L, "u@frc.utn.edu.ar", "Nombre", "Apellido", null));

        service.setEnabled(7L, false);

        assertThat(user.getEnabled()).isFalse();
        verify(refreshTokenService).revokeAllByUserId(7L);
    }

    @Test
    @DisplayName("setEnabled(true): habilita y NO revoca refresh tokens")
    void enableDoesNotRevoke() {
        User user = existing(7L, "u@frc.utn.edu.ar");
        user.setEnabled(false);
        when(userRepository.findById(7L)).thenReturn(Optional.of(user));
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(userMapper.toDto(any(User.class)))
                .thenReturn(new UserResponseDto(7L, "u@frc.utn.edu.ar", "Nombre", "Apellido", null));

        service.setEnabled(7L, true);

        assertThat(user.getEnabled()).isTrue();
        verify(refreshTokenService, never()).revokeAllByUserId(any());
    }

    @Test
    @DisplayName("setEnabled: usuario inexistente → ResourceNotFoundException")
    void setEnabledNotFoundThrows() {
        when(userRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.setEnabled(99L, false))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // ---- listados ----

    @Test
    @DisplayName("findEnabled: fuerza tamaño de página 15 aunque el cliente pida otro")
    void findEnabledForcesPageSize15() {
        Pageable requested = PageRequest.of(2, 100);
        Page<User> page = new PageImpl<>(List.of());
        when(userRepository.findAllByEnabled(eq(true), any(Pageable.class))).thenReturn(page);

        service.findEnabled(requested);

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(userRepository).findAllByEnabled(eq(true), captor.capture());
        assertThat(captor.getValue().getPageSize()).isEqualTo(15);
        assertThat(captor.getValue().getPageNumber()).isEqualTo(2);
    }

    @Test
    @DisplayName("findDisabled: consulta enabled=false con tamaño de página 15")
    void findDisabledForcesPageSize15() {
        Pageable requested = PageRequest.of(0, 50);
        when(userRepository.findAllByEnabled(eq(false), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));

        service.findDisabled(requested);

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(userRepository).findAllByEnabled(eq(false), captor.capture());
        assertThat(captor.getValue().getPageSize()).isEqualTo(15);
    }
}
