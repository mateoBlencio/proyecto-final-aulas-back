package ar.edu.utn.frc.siga.sysacad.internal.repository;

import ar.edu.utn.frc.siga.sysacad.internal.model.AutogestionNotifiedRoom;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

@Repository
public interface AutogestionNotifiedRoomRepository extends JpaRepository<AutogestionNotifiedRoom, Long> {

    List<AutogestionNotifiedRoom> findByOccurrenceIdIn(Collection<Long> occurrenceIds);
}
