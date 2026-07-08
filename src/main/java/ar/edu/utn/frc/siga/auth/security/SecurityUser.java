package ar.edu.utn.frc.siga.auth.security;

import ar.edu.utn.frc.siga.auth.model.Role;
import ar.edu.utn.frc.siga.auth.model.User;
import java.util.Collection;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.Getter;
import org.springframework.modulith.NamedInterface;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

/**
 * Envuelve {@link User} por composición para exponerlo como {@link UserDetails}.
 * {@code @NamedInterface("api")} deja el terreno preparado para que un módulo de negocio
 * inyecte {@code @AuthenticationPrincipal SecurityUser} el día que lo necesite (agregando
 * "auth :: api" a su propio allowedDependencies) — hoy ningún módulo de negocio lo consume.
 */
@NamedInterface("api")
@Getter
public class SecurityUser implements UserDetails {

    private final Integer id;
    private final String email;
    private final String password;
    private final Set<Role> roles;
    private final boolean enabled;

    public SecurityUser(Integer id, String email, String password, Set<Role> roles, boolean enabled) {
        this.id = id;
        this.email = email;
        this.password = password;
        this.roles = roles;
        this.enabled = enabled;
    }

    public static SecurityUser fromUser(User user) {
        return new SecurityUser(user.getId(), user.getEmail(), user.getPasswordHash(),
                user.getRoles(), Boolean.TRUE.equals(user.getEnabled()));
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return roles.stream()
                .map(role -> new SimpleGrantedAuthority("ROLE_" + role.name()))
                .collect(Collectors.toSet());
    }

    @Override
    public String getPassword() {
        return password;
    }

    @Override
    public String getUsername() {
        return email;
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }
}
