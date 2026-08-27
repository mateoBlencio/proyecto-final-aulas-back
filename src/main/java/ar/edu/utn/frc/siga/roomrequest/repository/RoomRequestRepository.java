package ar.edu.utn.frc.siga.roomrequest.repository;

import ar.edu.utn.frc.siga.roomrequest.model.RoomRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface RoomRequestRepository extends JpaRepository<RoomRequest, Long> {
}
