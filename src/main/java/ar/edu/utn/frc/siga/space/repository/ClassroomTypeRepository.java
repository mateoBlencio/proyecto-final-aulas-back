package ar.edu.utn.frc.siga.space.repository;

import ar.edu.utn.frc.siga.space.model.ClassroomType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ClassroomTypeRepository extends JpaRepository<ClassroomType, Integer> {

    Optional<ClassroomType> findByIdAndDeletedFalse(Integer id);

    Optional<ClassroomType> findByDescriptionIgnoreCaseAndDeletedFalse(String description);

}
