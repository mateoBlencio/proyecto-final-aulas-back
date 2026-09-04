package ar.edu.utn.frc.siga.auth.repository;

import ar.edu.utn.frc.siga.auth.model.Role;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RoleRepository extends JpaRepository<Role, Long> {

    boolean existsByName(String name);

    Optional<Role> findByName(String name);
}
