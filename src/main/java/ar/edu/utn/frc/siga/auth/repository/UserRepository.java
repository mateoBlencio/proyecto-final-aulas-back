package ar.edu.utn.frc.siga.auth.repository;

import ar.edu.utn.frc.siga.auth.model.User;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, Integer> {

    Optional<User> findByEmailAndDeletedFalse(String email);
}
