package ar.edu.utn.frc.siga.auth.security;

import ar.edu.utn.frc.siga.auth.model.RoleAssignment;
import ar.edu.utn.frc.siga.auth.model.User;
import ar.edu.utn.frc.siga.common.security.Authorities;
import ar.edu.utn.frc.siga.common.security.BuildingScope;
import ar.edu.utn.frc.siga.common.security.Permission;
import ar.edu.utn.frc.siga.common.security.ScopeType;
import java.util.Collection;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.Getter;
import org.springframework.modulith.NamedInterface;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

@NamedInterface("api")
@Getter
public class SecurityUser implements UserDetails {

    private final Long id;
    private final String email;
    private final String password;
    private final boolean enabled;
    private final Map<Permission, BuildingScope> scopesByPermission;

    public SecurityUser(Long id, String email, String password, boolean enabled,
                         Map<Permission, BuildingScope> scopesByPermission) {
        this.id = id;
        this.email = email;
        this.password = password;
        this.enabled = enabled;
        this.scopesByPermission = scopesByPermission;
    }

    public static SecurityUser fromUser(User user) {
        return new SecurityUser(user.getId(), user.getEmail(), user.getPasswordHash(),
                Boolean.TRUE.equals(user.getEnabled()), resolveScopes(user.getRoleAssignments()));
    }

    private static Map<Permission, BuildingScope> resolveScopes(List<RoleAssignment> assignments) {
        Map<Permission, BuildingScope> scopes = new EnumMap<>(Permission.class);
        for (RoleAssignment assignment : assignments) {
            for (Permission permission : assignment.getRole().getPermissions()) {
                BuildingScope granted = grantedScope(permission, assignment);
                scopes.merge(permission, granted, BuildingScope::union);
            }
        }
        return scopes;
    }

    private static BuildingScope grantedScope(Permission permission, RoleAssignment assignment) {
        if (permission.scopeType() == ScopeType.GLOBAL) {
            return BuildingScope.unrestricted();
        }
        if (assignment.getScopeType() == ScopeType.GLOBAL) {
            return BuildingScope.unrestricted();
        }
        return BuildingScope.of(Set.of(assignment.getScopeId()));
    }

    public BuildingScope scopeFor(Permission permission) {
        return scopesByPermission.getOrDefault(permission, BuildingScope.denied());
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return scopesByPermission.keySet().stream()
                .map(permission -> new SimpleGrantedAuthority(Authorities.of(permission)))
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
