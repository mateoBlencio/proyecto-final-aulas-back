package ar.edu.utn.frc.siga.notification.service.impl;

import ar.edu.utn.frc.siga.notification.model.NotificationStatus;
import ar.edu.utn.frc.siga.notification.model.OutboundNotification;
import ar.edu.utn.frc.siga.notification.repository.OutboundNotificationRepository;
import ar.edu.utn.frc.siga.roomrequest.model.RoomRequestResolved;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.ObjectMapper;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("RoomRequestResolvedListener")
class RoomRequestResolvedListenerTest {

    @Mock
    private OutboundNotificationRepository repository;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    @DisplayName("on: registra la notificación pendiente con el email del docente y el payload del evento")
    void onRegistraNotificacionPendiente() {
        RoomRequestResolvedListener listener = new RoomRequestResolvedListener(repository, objectMapper);
        RoomRequestResolved event = new RoomRequestResolved(1L, 2L, "Ada Lovelace", "ada@frc.utn.edu.ar",
                42L, LocalDate.of(2026, 9, 1), DayOfWeek.TUESDAY, LocalTime.of(10, 0), List.of(11L, 12L));
        when(repository.save(org.mockito.ArgumentMatchers.any()))
                .thenAnswer(invocation -> invocation.getArgument(0));

        listener.on(event);

        ArgumentCaptor<OutboundNotification> captor = ArgumentCaptor.forClass(OutboundNotification.class);
        verify(repository).save(captor.capture());
        OutboundNotification saved = captor.getValue();
        assertThat(saved.getRecipient()).isEqualTo("ada@frc.utn.edu.ar");
        assertThat(saved.getChannel()).isEqualTo("EMAIL");
        assertThat(saved.getStatus()).isEqualTo(NotificationStatus.PENDING);
        assertThat(saved.getPayload()).contains("\"itemId\":1").contains("ada@frc.utn.edu.ar");
    }
}
