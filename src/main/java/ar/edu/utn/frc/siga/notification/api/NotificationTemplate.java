package ar.edu.utn.frc.siga.notification.api;

import org.springframework.modulith.NamedInterface;

@NamedInterface("api")
public enum NotificationTemplate {
    ROOM_REQUEST_RESOLVED,
    ROOM_REQUEST_TAKEN_OVER,
    BUILDING_ACTIVITY,
    SYSACAD_ALERT
}
