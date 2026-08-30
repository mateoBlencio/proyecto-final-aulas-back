package ar.edu.utn.frc.siga.space.repository;

import ar.edu.utn.frc.siga.common.repository.SoftDeletableRepository;
import ar.edu.utn.frc.siga.space.model.ClassroomType;
import java.util.Optional;
import org.springframework.stereotype.Repository;

@Repository
public interface ClassroomTypeRepository extends SoftDeletableRepository<ClassroomType, Long> {

    Optional<ClassroomType> findByDescriptionIgnoreCaseAndDeletedAtIsNull(String description);

}
