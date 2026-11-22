package ar.edu.utn.frc.siga.notification.internal.service;

import java.util.List;

record NotificationsQueuedEvent(List<Long> notificationIds) {
}
