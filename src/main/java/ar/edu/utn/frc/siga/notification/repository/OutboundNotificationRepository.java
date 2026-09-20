package ar.edu.utn.frc.siga.notification.repository;

import ar.edu.utn.frc.siga.notification.model.OutboundNotification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface OutboundNotificationRepository extends JpaRepository<OutboundNotification, Long> {
}
