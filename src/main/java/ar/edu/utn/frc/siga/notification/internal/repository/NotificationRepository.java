package ar.edu.utn.frc.siga.notification.internal.repository;

import ar.edu.utn.frc.siga.notification.internal.model.Notification;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NotificationRepository extends JpaRepository<Notification, Long> {
}
