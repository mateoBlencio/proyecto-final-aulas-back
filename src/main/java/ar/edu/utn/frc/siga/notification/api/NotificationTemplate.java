package ar.edu.utn.frc.siga.notification.api;

import org.springframework.modulith.NamedInterface;

import java.time.Duration;
import java.util.Arrays;

@NamedInterface("api")
public enum NotificationTemplate {
    ROOM_REQUEST_RESOLVED,
    ROOM_REQUEST_TAKEN_OVER,
    BUILDING_ACTIVITY,
    SYSACAD_ALERT,
    PASSWORD_RESET(true, Duration.ofMinutes(30)),
    PASSWORD_CHANGED;

    private final boolean sensitive;
    private final Duration expiresAfter;

    NotificationTemplate() {
        this(false, null);
    }

    NotificationTemplate(boolean sensitive, Duration expiresAfter) {
        this.sensitive = sensitive;
        this.expiresAfter = expiresAfter;
    }

    public boolean sensitive() {
        return sensitive;
    }

    public Duration expiresAfter() {
        return expiresAfter;
    }

    public static boolean isSensitive(String name) {
        return find(name) != null && find(name).sensitive;
    }

    public static Duration expiresAfter(String name) {
        NotificationTemplate template = find(name);
        return template == null ? null : template.expiresAfter;
    }

    private static NotificationTemplate find(String name) {
        return Arrays.stream(values()).filter(template -> template.name().equals(name)).findFirst().orElse(null);
    }
}
