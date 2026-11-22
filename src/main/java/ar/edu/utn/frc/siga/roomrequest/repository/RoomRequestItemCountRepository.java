package ar.edu.utn.frc.siga.roomrequest.repository;

import ar.edu.utn.frc.siga.roomrequest.model.RoomRequestItem;
import ar.edu.utn.frc.siga.roomrequest.model.RoomRequestStatus;
import java.util.Map;
import org.springframework.data.jpa.domain.Specification;

public interface RoomRequestItemCountRepository {

    Map<RoomRequestStatus, Long> countByStatus(Specification<RoomRequestItem> spec);
}
