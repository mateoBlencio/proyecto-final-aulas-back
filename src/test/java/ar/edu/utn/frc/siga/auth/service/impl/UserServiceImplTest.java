package ar.edu.utn.frc.siga.auth.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ar.edu.utn.frc.siga.auth.config.AuthDomainProperties;
import ar.edu.utn.frc.siga.auth.dto.request.CreateUserRequestDto;
import ar.edu.utn.frc.siga.auth.dto.response.UserResponseDto;
import ar.edu.utn.frc.siga.auth.exception.FieldValidationException;
import ar.edu.utn.frc.siga.auth.exception.UserDomainException;
import ar.edu.utn.frc.siga.auth.mapper.RoleAssignmentComposer;
import ar.edu.utn.frc.siga.auth.mapper.UserMapper;
import ar.edu.utn.frc.siga.auth.model.SystemRole;
import ar.edu.utn.frc.siga.auth.model.User;
import ar.edu.utn.frc.siga.auth.repository.UserRepository;
import ar.edu.utn.frc.siga.auth.service.RefreshTokenService;
import ar.edu.utn.frc.siga.auth.service.RoleAssignmentService;
import ar.edu.utn.frc.siga.common.exception.InvalidSortPropertyException;
import ar.edu.utn.frc.siga.common.exception.ResourceNotFoundException;
import java.util.List;
import java.util.Optional;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
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

    private final PasswordPolicy passwordPolicy = new PasswordPolicy();

    private UserServiceImpl service;

    private static final String CREATOR_EMAIL = "admin@frc.utn.edu.ar";

    @BeforeEach
    void setUp() {
        service = new UserServiceImpl(userRepository, passwordEncoder, passwordPolicy, refreshTokenService,
                userMapper, roleAssignmentComposer, roleAssignmentService, authDomainProperties);
        lenient().when(roleAssignmentComposer.composeAll(any())).thenReturn(List.of());
    }

    private CreateUserRequestDto createDto(String email) {
        return createDto(email, "clave-segura-2026");
    }

    private CreateUserRequestDto createDto(String email, String password) {
        return new CreateUserRequestDto(email, password, "Nombre", "Apellido", null);
    }

    private static String twoByteChars(int count) {
        return IntStream.range(0, count)
                .mapToObj(i -> new String(Character.toChars(0xC0 + i)))
                .collect(Collectors.joining());
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
        when(passwordEncoder.encode("clave-segura-2026")).thenReturn("HASH");
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
    @DisplayName("create con AuthDomainProperties real: subdominio institucional → guarda el usuario")
    void createSubdomainWithRealDomainProperties() {
        UserServiceImpl serviceWithRealDomain = new UserServiceImpl(userRepository, passwordEncoder, passwordPolicy,
                refreshTokenService, userMapper, roleAssignmentComposer, roleAssignmentService,
                new AuthDomainProperties());
        String email = "jperez@sistemas.frc.utn.edu.ar";
        when(userRepository.existsByEmail(email)).thenReturn(false);
        when(passwordEncoder.encode("clave-segura-2026")).thenReturn("HASH");
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));
        when(userMapper.toDto(any(User.class)))
                .thenReturn(new UserResponseDto(1L, email, "Nombre", "Apellido", List.of()));

        serviceWithRealDomain.create(createDto(email), CREATOR_EMAIL);

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        assertThat(captor.getValue().getEmail()).isEqualTo(email);
    }

    @Test
    @DisplayName("create: email con espacios y mayúsculas → valida, consulta existencia y guarda el email normalizado")
    void createNormalizesEmail() {
        String normalized = "jperez@sistemas.frc.utn.edu.ar";
        when(authDomainProperties.isAllowedEmail(normalized)).thenReturn(true);
        when(userRepository.existsByEmail(normalized)).thenReturn(false);
        when(passwordEncoder.encode("clave-segura-2026")).thenReturn("HASH");
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));
        when(userMapper.toDto(any(User.class)))
                .thenReturn(new UserResponseDto(1L, normalized, "Nombre", "Apellido", List.of()));

        service.create(createDto("  JPerez@Sistemas.FRC.utn.edu.ar "), CREATOR_EMAIL);

        verify(userRepository).existsByEmail(normalized);
        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        assertThat(captor.getValue().getEmail()).isEqualTo(normalized);
    }

    @Test
    @DisplayName("create: password de 40 caracteres distintos de 2 bytes (80 bytes UTF-8) → FieldValidationException en password y no guarda")
    void createPasswordOver72BytesThrows() {
        when(authDomainProperties.isAllowedEmail(any())).thenReturn(true);
        when(userRepository.existsByEmail("nuevo@frc.utn.edu.ar")).thenReturn(false);

        assertThatThrownBy(() -> service.create(createDto("nuevo@frc.utn.edu.ar", twoByteChars(40)), CREATOR_EMAIL))
                .isInstanceOfSatisfying(FieldValidationException.class, e ->
                        assertThat((Map<Object, Object>) e.getProperties().get("errors")).containsOnlyKeys("password"));

        verify(passwordEncoder, never()).encode(any());
        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("create: password de 73 bytes UTF-8 (36 caracteres distintos de 2 bytes y uno de 1) → FieldValidationException")
    void createPassword73BytesThrows() {
        when(authDomainProperties.isAllowedEmail(any())).thenReturn(true);
        when(userRepository.existsByEmail("nuevo@frc.utn.edu.ar")).thenReturn(false);

        assertThatThrownBy(() -> service.create(createDto("nuevo@frc.utn.edu.ar", twoByteChars(36) + "a"), CREATOR_EMAIL))
                .isInstanceOf(FieldValidationException.class);

        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("create: password de exactamente 72 bytes UTF-8 (36 caracteres distintos de 2 bytes) → hashea y guarda")
    void createPasswordOfExactly72BytesPasses() {
        String password = twoByteChars(36);
        when(authDomainProperties.isAllowedEmail(any())).thenReturn(true);
        when(userRepository.existsByEmail("nuevo@frc.utn.edu.ar")).thenReturn(false);
        when(passwordEncoder.encode(password)).thenReturn("HASH");
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));
        when(userMapper.toDto(any(User.class)))
                .thenReturn(new UserResponseDto(1L, "nuevo@frc.utn.edu.ar", "Nombre", "Apellido", List.of()));

        service.create(createDto("nuevo@frc.utn.edu.ar", password), CREATOR_EMAIL);

        verify(userRepository).save(any(User.class));
    }

    @Test
    @DisplayName("hasRole y findBuildingIdsForRole consultan el repositorio con el email normalizado")
    void roleQueriesNormalizeEmail() {
        String normalized = "jperez@sistemas.frc.utn.edu.ar";
        String raw = " JPerez@Sistemas.FRC.utn.edu.ar ";
        when(userRepository.existsEnabledRoleForUser(normalized, SystemRole.AUXILIAR_AULICO)).thenReturn(true);
        when(userRepository.findBuildingIdsForUserAndRole(normalized, SystemRole.AUXILIAR_AULICO))
                .thenReturn(Set.of(3L));

        assertThat(service.hasRole(raw, SystemRole.AUXILIAR_AULICO)).isTrue();
        assertThat(service.findBuildingIdsForRole(raw, SystemRole.AUXILIAR_AULICO)).containsExactly(3L);
    }

    @Test
    @DisplayName("create con AuthDomainProperties real: dominio externo → UserDomainException y no guarda")
    void createExternalDomainWithRealDomainProperties() {
        UserServiceImpl serviceWithRealDomain = new UserServiceImpl(userRepository, passwordEncoder, passwordPolicy,
                refreshTokenService, userMapper, roleAssignmentComposer, roleAssignmentService,
                new AuthDomainProperties());

        assertThatThrownBy(() -> serviceWithRealDomain.create(
                createDto("jperez@sistemas.frc.utn.edu.ar.evil.com"), CREATOR_EMAIL))
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

        service.setEnabled(7L, false, CREATOR_EMAIL);

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

        service.setEnabled(7L, true, CREATOR_EMAIL);

        assertThat(user.getEnabled()).isTrue();
        verify(refreshTokenService, never()).revokeAllByUserId(any());
    }

    @Test
    @DisplayName("setEnabled: usuario inexistente → ResourceNotFoundException")
    void setEnabledNotFoundThrows() {
        when(userRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.setEnabled(99L, false, CREATOR_EMAIL))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("setEnabled(false): el usuario no puede inhabilitarse a sí mismo")
    void disableSelfThrows() {
        User user = existing(7L, "u@frc.utn.edu.ar");
        when(userRepository.findById(7L)).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> service.setEnabled(7L, false, "u@frc.utn.edu.ar"))
                .isInstanceOf(UserDomainException.class)
                .hasMessage("Users cannot disable themselves");

        assertThat(user.getEnabled()).isTrue();
        verify(userRepository, never()).save(any());
        verify(refreshTokenService, never()).revokeAllByUserId(any());
    }

    @Test
    @DisplayName("setEnabled(false): el email del usuario actual se normaliza antes de comparar")
    void disableSelfWithDifferentCaseThrows() {
        User user = existing(7L, "u@frc.utn.edu.ar");
        when(userRepository.findById(7L)).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> service.setEnabled(7L, false, "  U@Frc.UTN.edu.ar "))
                .isInstanceOf(UserDomainException.class)
                .hasMessage("Users cannot disable themselves");

        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("setEnabled(false): la única ADMINISTRADOR habilitada no se puede inhabilitar")
    void disableLastAdministratorThrows() {
        User user = existing(7L, "sub@frc.utn.edu.ar");
        when(userRepository.findById(7L)).thenReturn(Optional.of(user));
        when(userRepository.findEnabledByRole(SystemRole.ADMINISTRADOR)).thenReturn(List.of(user));

        assertThatThrownBy(() -> service.setEnabled(7L, false, CREATOR_EMAIL))
                .isInstanceOf(UserDomainException.class)
                .hasMessage("The last enabled ADMINISTRADOR cannot be disabled");

        assertThat(user.getEnabled()).isTrue();
        verify(userRepository, never()).save(any());
        verify(refreshTokenService, never()).revokeAllByUserId(any());
    }

    @Test
    @DisplayName("setEnabled(false): con dos ADMINISTRADOR habilitadas se puede inhabilitar a una y se revocan sus tokens")
    void disableOneOfTwoAdministratorsPasses() {
        User user = existing(7L, "sub@frc.utn.edu.ar");
        User other = existing(8L, "otra.sub@frc.utn.edu.ar");
        when(userRepository.findById(7L)).thenReturn(Optional.of(user));
        when(userRepository.findEnabledByRole(SystemRole.ADMINISTRADOR)).thenReturn(List.of(user, other));
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(userMapper.toDto(any(User.class)))
                .thenReturn(new UserResponseDto(7L, "sub@frc.utn.edu.ar", "Nombre", "Apellido", null));

        service.setEnabled(7L, false, CREATOR_EMAIL);

        assertThat(user.getEnabled()).isFalse();
        verify(refreshTokenService).revokeAllByUserId(7L);
    }

    @Test
    @DisplayName("setEnabled(false): un usuario que no es la ADMINISTRADOR se inhabilita aunque quede una sola habilitada")
    void disableAuxiliarWithSingleEnabledAdministratorPasses() {
        User auxiliar = existing(7L, "aux@frc.utn.edu.ar");
        User onlyAdministrator = existing(8L, "sub@frc.utn.edu.ar");
        when(userRepository.findById(7L)).thenReturn(Optional.of(auxiliar));
        when(userRepository.findEnabledByRole(SystemRole.ADMINISTRADOR)).thenReturn(List.of(onlyAdministrator));
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(userMapper.toDto(any(User.class)))
                .thenReturn(new UserResponseDto(7L, "aux@frc.utn.edu.ar", "Nombre", "Apellido", null));

        service.setEnabled(7L, false, CREATOR_EMAIL);

        assertThat(auxiliar.getEnabled()).isFalse();
        verify(refreshTokenService).revokeAllByUserId(7L);
    }

    @Test
    @DisplayName("setEnabled(true): habilitar no valida autoinhabilitación ni consulta las ADMINISTRADOR")
    void enableSkipsDisableValidations() {
        User user = existing(7L, "u@frc.utn.edu.ar");
        user.setEnabled(false);
        when(userRepository.findById(7L)).thenReturn(Optional.of(user));
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(userMapper.toDto(any(User.class)))
                .thenReturn(new UserResponseDto(7L, "u@frc.utn.edu.ar", "Nombre", "Apellido", null));

        service.setEnabled(7L, true, "u@frc.utn.edu.ar");

        assertThat(user.getEnabled()).isTrue();
        verify(userRepository, never()).findEnabledByRole(any());
    }

    // ---- listados ----

    @Test
    @DisplayName("findEnabled: un sort por una propiedad fuera de la lista (passwordHash) da InvalidSortPropertyException")
    void findEnabledRejectsUnlistedSort() {
        assertThatThrownBy(() -> service.findEnabled(PageRequest.of(0, 15, Sort.by("passwordHash"))))
                .isInstanceOf(InvalidSortPropertyException.class)
                .hasMessageContaining("passwordHash");

        verify(userRepository, never()).findAllByEnabled(anyBoolean(), any());
    }

    @Test
    @DisplayName("findDisabled: un sort por una propiedad fuera de la lista (passwordHash) da InvalidSortPropertyException")
    void findDisabledRejectsUnlistedSort() {
        assertThatThrownBy(() -> service.findDisabled(PageRequest.of(0, 15, Sort.by("passwordHash"))))
                .isInstanceOf(InvalidSortPropertyException.class);

        verify(userRepository, never()).findAllByEnabled(anyBoolean(), any());
    }

    @ParameterizedTest
    @ValueSource(strings = {"id", "email", "firstName", "lastName"})
    @DisplayName("findEnabled: acepta ordenar por id, email, firstName y lastName y conserva el sort")
    void findEnabledAcceptsListedSort(String property) {
        when(userRepository.findAllByEnabled(eq(true), any(Pageable.class))).thenReturn(new PageImpl<>(List.of()));

        service.findEnabled(PageRequest.of(0, 15, Sort.by(property)));

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(userRepository).findAllByEnabled(eq(true), captor.capture());
        assertThat(captor.getValue().getSort()).isEqualTo(Sort.by(property));
    }

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
