package ar.edu.utn.frc.classroom_allocation.space.repository;

import ar.edu.utn.frc.classroom_allocation.space.model.Classroom;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

@Repository
public interface ClassroomRepository extends JpaRepository<Classroom, Integer>, JpaSpecificationExecutor<Classroom> {

    Optional<Classroom> findByIdAndDeletedFalse(Integer id);

    Optional<Classroom> findByRoomNumberAndDeletedFalse(String roomNumber);

}
