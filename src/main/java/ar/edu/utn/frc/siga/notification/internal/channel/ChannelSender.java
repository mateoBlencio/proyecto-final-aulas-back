package ar.edu.utn.frc.siga.notification.internal.channel;

import ar.edu.utn.frc.siga.notification.api.NotificationChannel;
import ar.edu.utn.frc.siga.notification.api.NotificationRecipient;
import ar.edu.utn.frc.siga.notification.internal.render.RenderedNotification;

public interface ChannelSender {

    NotificationChannel channel();

    void send(RenderedNotification message, NotificationRecipient recipient);
}
