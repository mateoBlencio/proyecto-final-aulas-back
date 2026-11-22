package ar.edu.utn.frc.siga.roomrequest.scheduler;

import ar.edu.utn.frc.siga.roomrequest.service.RoomRequestExpiryService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.scheduling.annotation.Scheduled;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("RoomRequestAcceptanceScheduler")
class RoomRequestAcceptanceSchedulerTest {

    @Mock
    private RoomRequestExpiryService expiryService;

    @Test
    @DisplayName("cada corrida delega la aceptación automática de los NOTIFIED vencidos")
    void delegatesToTheExpiryService() {
        new RoomRequestAcceptanceScheduler(expiryService).acceptOverdueNotifiedItems();

        verify(expiryService).acceptOverdueNotifiedItems();
    }

    @Test
    @DisplayName("el cron sale de siga.room-requests.acceptance.cron y corre en hora de Buenos Aires")
    void cronIsConfigurableAndLocal() throws NoSuchMethodException {
        Scheduled scheduled = RoomRequestAcceptanceScheduler.class
                .getMethod("acceptOverdueNotifiedItems").getAnnotation(Scheduled.class);

        assertThat(scheduled.cron()).isEqualTo("${siga.room-requests.acceptance.cron}");
        assertThat(scheduled.zone()).isEqualTo("America/Argentina/Buenos_Aires");
    }
}
