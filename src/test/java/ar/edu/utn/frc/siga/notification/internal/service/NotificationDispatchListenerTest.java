package ar.edu.utn.frc.siga.notification.internal.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationDispatchListenerTest {

    @Mock
    private NotificationRetryClaimer claimer;
    @Mock
    private NotificationDispatcher dispatcher;

    @Test
    void dispatchesOnlyTheIdsThatTheClaimerReclaimed() {
        NotificationDispatchListener listener = new NotificationDispatchListener(claimer, dispatcher);
        when(claimer.claim(List.of(1L, 2L))).thenReturn(List.of(2L));

        listener.onNotificationsQueued(new NotificationsQueuedEvent(List.of(1L, 2L)));

        verify(dispatcher, times(1)).dispatch(2L);
        verify(dispatcher, never()).dispatch(1L);
    }
}
