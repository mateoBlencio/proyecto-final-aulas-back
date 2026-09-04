package ar.edu.utn.frc.siga.auth.service.impl;

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
import ar.edu.utn.frc.siga.auth.service.UserService;
import ar.edu.utn.frc.siga.common.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private static final int PAGE_SIZE = 15;

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final RefreshTokenService refreshTokenService;
    private final UserMapper userMapper;
    private final RoleAssignmentComposer roleAssignmentComposer;
    private final RoleAssignmentService roleAssignmentService;
    private final AuthDomainProperties authDomainProperties;

    @Override
    @Transactional
    public UserResponseDto create(CreateUserRequestDto dto, String currentUserEmail) {
        String email = dto.email();
        log.debug("Creando usuario: email={}", email);

        if (!authDomainProperties.isAllowedEmail(email)) {
            log.warn("Alta de usuario rechazada: dominio no institucional, email={}", email);
            throw new UserDomainException("User email domain not allowed: " + email);
        }
        if (userRepository.existsByEmail(email)) {
            log.warn("Alta de usuario rechazada: email '{}' ya existe", email);
            throw new UserDomainException("User email already exists: " + email);
        }

        User user = User.builder()
                .email(email)
                .passwordHash(passwordEncoder.encode(dto.password()))
                .enabled(true)
                .firstName(dto.firstName())
                .lastName(dto.lastName())
                .build();

        User saved = userRepository.save(user);
        log.info("Usuario creado: id={}, email={}", saved.getId(), saved.getEmail());

        if (dto.initialRole() != null) {
            roleAssignmentService.assign(saved.getId(), dto.initialRole(), currentUserEmail);
        }

        return toDto(saved);
    }

    @Override
    @Transactional
    public UserResponseDto setEnabled(Long id, boolean enabled) {
        log.debug("{} usuario: id={}", enabled ? "Habilitando" : "Inhabilitando", id);
        User user = findExisting(id);
        user.setEnabled(enabled);
        User saved = userRepository.save(user);

        if (!enabled) {
            refreshTokenService.revokeAllByUserId(id);
        }
        log.info("Usuario {}: id={}", enabled ? "habilitado" : "inhabilitado", id);
        return toDto(saved);
    }

    @Override
    public Page<UserResponseDto> findEnabled(Pageable pageable) {
        log.debug("Listando usuarios habilitados: page={}", pageable.getPageNumber());
        return userRepository.findAllByEnabled(true, fixedSize(pageable)).map(this::toDto);
    }

    @Override
    public Page<UserResponseDto> findDisabled(Pageable pageable) {
        log.debug("Listando usuarios inhabilitados: page={}", pageable.getPageNumber());
        return userRepository.findAllByEnabled(false, fixedSize(pageable)).map(this::toDto);
    }

    private User findExisting(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> {
                    log.warn("Usuario no encontrado: id={}", id);
                    return ResourceNotFoundException.of("User", id);
                });
    }

    private Pageable fixedSize(Pageable pageable) {
        return PageRequest.of(pageable.getPageNumber(), PAGE_SIZE, pageable.getSort());
    }

    private UserResponseDto toDto(User user) {
        UserResponseDto base = userMapper.toDto(user);
        return new UserResponseDto(base.id(), base.email(), base.firstName(), base.lastName(),
                roleAssignmentComposer.composeAll(user.getRoleAssignments()));
    }
}
