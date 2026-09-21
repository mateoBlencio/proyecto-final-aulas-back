package ar.edu.utn.frc.siga.auth.repository;

import ar.edu.utn.frc.siga.auth.model.SystemRole;
import ar.edu.utn.frc.siga.auth.model.User;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserRepository extends JpaRepository<User, Long> {

    @EntityGraph(attributePaths = {"roleAssignments", "roleAssignments.role"})
    Optional<User> findByEmailAndEnabledTrue(String email);

    boolean existsByEmail(String email);

    Page<User> findAllByEnabled(boolean enabled, Pageable pageable);

    /**
     * Usuarios habilitados cuyo rol {@code role} alcanza el edificio {@code buildingId}: los acotados
     * a ese edificio y también los de alcance GLOBAL, que cubren cualquier edificio.
     */
    @EntityGraph(attributePaths = {"roleAssignments", "roleAssignments.role"})
    @Query("""
            select distinct u from User u
            join u.roleAssignments ra
            where u.enabled = true
              and ra.role = :role
              and (ra.scopeType = ar.edu.utn.frc.siga.common.security.ScopeType.GLOBAL
                   or ra.scopeId = :buildingId)
            """)
    List<User> findEnabledByRoleCoveringBuilding(@Param("role") SystemRole role,
                                                 @Param("buildingId") Long buildingId);

    @Query("""
            select (count(ra) > 0) from RoleAssignment ra
            where ra.user.enabled = true and ra.role = :role
              and ra.scopeType = ar.edu.utn.frc.siga.common.security.ScopeType.GLOBAL
            """)
    boolean existsEnabledGlobalRole(@Param("role") SystemRole role);

    @Query("""
            select distinct ra.scopeId from RoleAssignment ra
            where ra.user.enabled = true and ra.role = :role
              and ra.scopeType = ar.edu.utn.frc.siga.common.security.ScopeType.BUILDING
              and ra.scopeId in :buildingIds
            """)
    Set<Long> findBuildingIdsWithScopedRole(@Param("role") SystemRole role,
                                            @Param("buildingIds") Collection<Long> buildingIds);

    @Query("""
            select (count(ra) > 0) from RoleAssignment ra
            where ra.user.email = :email and ra.user.enabled = true and ra.role = :role
            """)
    boolean existsEnabledRoleForUser(@Param("email") String email, @Param("role") SystemRole role);

    @Query("""
            select ra.scopeId from RoleAssignment ra
            where ra.user.email = :email and ra.user.enabled = true and ra.role = :role
              and ra.scopeType = ar.edu.utn.frc.siga.common.security.ScopeType.BUILDING
            """)
    Set<Long> findBuildingIdsForUserAndRole(@Param("email") String email, @Param("role") SystemRole role);
}
