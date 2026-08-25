package ar.edu.utn.frc.siga.auth.repository;

import ar.edu.utn.frc.siga.auth.model.User;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmailAndEnabledTrue(String email);

    boolean existsByEmail(String email);

    Page<User> findAllByEnabled(boolean enabled, Pageable pageable);
}
