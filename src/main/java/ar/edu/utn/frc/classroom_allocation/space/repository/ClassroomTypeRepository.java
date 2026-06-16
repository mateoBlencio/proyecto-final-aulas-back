package ar.edu.utn.frc.classroom_allocation.space.repository;

import ar.edu.utn.frc.classroom_allocation.space.model.ClassroomType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ClassroomTypeRepository extends JpaRepository<ClassroomType, Integer> {

    Optional<ClassroomType> findByIdAndDeletedFalse(Integer id);

}
