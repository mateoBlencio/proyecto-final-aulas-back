package ar.edu.utn.frc.siga.auth.service.impl;

import ar.edu.utn.frc.siga.auth.config.AuthDomainProperties;
import ar.edu.utn.frc.siga.auth.dto.request.CreateUserRequestDto;
import ar.edu.utn.frc.siga.auth.dto.response.UserResponseDto;
import ar.edu.utn.frc.siga.auth.exception.UserDomainException;
import ar.edu.utn.frc.siga.auth.mapper.UserMapper;
import ar.edu.utn.frc.siga.auth.model.Role;
import ar.edu.utn.frc.siga.auth.model.User;
import ar.edu.utn.frc.siga.auth.repository.UserRepository;
import ar.edu.utn.frc.siga.auth.service.RefreshTokenService;
import ar.edu.utn.frc.siga.auth.service.UserService;
import ar.edu.utn.frc.siga.common.exception.ResourceNotFoundException;
import java.util.HashSet;
import java.util.Set;
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
    private final AuthDomainProperties authDomainProperties;

    @Override
    @Transactional
    public UserResponseDto create(CreateUserRequestDto dto) {
        String email = dto.email();
        log.debug("Creando usuario: email={}, rol={}", email, dto.rol());
        Role parsedRole = parseRole(dto.rol());

        if (!email.toLowerCase().endsWith("@" + authDomainProperties.getAllowedEmailDomain().toLowerCase())) {
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
                .roles(mutableRoleSet(parsedRole))
                .build();

        User saved = userRepository.save(user);
        log.info("Usuario creado: id={}, email={}", saved.getId(), saved.getEmail());
        return userMapper.toDto(saved);
    }

    @Override
    @Transactional
    public UserResponseDto setEnabled(Integer id, boolean enabled) {
        log.debug("{} usuario: id={}", enabled ? "Habilitando" : "Inhabilitando", id);
        User user = findExisting(id);
        user.setEnabled(enabled);
        User saved = userRepository.save(user);

        if (!enabled) {
            refreshTokenService.revokeAllByUserId(id);
        }
        log.info("Usuario {}: id={}", enabled ? "habilitado" : "inhabilitado", id);
        return userMapper.toDto(saved);
    }

    @Override
    @Transactional
    public UserResponseDto changeRole(Integer id, String rol, String currentUserEmail) {
        log.debug("Cambiando rol: id={}, rol={}", id, rol);
        Role parsedRole = parseRole(rol);
        User user = findExisting(id);

        if (user.getEmail().equalsIgnoreCase(currentUserEmail)) {
            log.warn("Cambio de rol rechazado: el usuario id={} intentó editar su propio rol", id);
            throw new UserDomainException("A user cannot change their own role");
        }

        user.setRoles(mutableRoleSet(parsedRole));
        User saved = userRepository.save(user);
        refreshTokenService.revokeAllByUserId(id);
        log.info("Rol de usuario actualizado: id={}, rol={}", id, parsedRole);
        return userMapper.toDto(saved);
    }

    private Role parseRole(String rol) {
        try {
            return Role.valueOf(rol);
        } catch (IllegalArgumentException e) {
            log.warn("Rol inválido: '{}'", rol);
            throw new UserDomainException("Invalid role: " + rol);
        }
    }

    @Override
    public Page<UserResponseDto> findEnabled(Pageable pageable) {
        log.debug("Listando usuarios habilitados: page={}", pageable.getPageNumber());
        return userRepository.findAllByEnabled(true, fixedSize(pageable)).map(userMapper::toDto);
    }

    @Override
    public Page<UserResponseDto> findDisabled(Pageable pageable) {
        log.debug("Listando usuarios inhabilitados: page={}", pageable.getPageNumber());
        return userRepository.findAllByEnabled(false, fixedSize(pageable)).map(userMapper::toDto);
    }

    private User findExisting(Integer id) {
        return userRepository.findById(id)
                .orElseThrow(() -> {
                    log.warn("Usuario no encontrado: id={}", id);
                    return ResourceNotFoundException.of("User", id);
                });
    }

    private Pageable fixedSize(Pageable pageable) {
        return PageRequest.of(pageable.getPageNumber(), PAGE_SIZE, pageable.getSort());
    }

    private Set<Role> mutableRoleSet(Role rol) {
        Set<Role> roles = new HashSet<>();
        roles.add(rol);
        return roles;
    }
}
