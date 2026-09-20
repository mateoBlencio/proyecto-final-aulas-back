package ar.edu.utn.frc.siga.notification;

import ar.edu.utn.frc.siga.notification.api.NotificationRecipient;
import ar.edu.utn.frc.siga.notification.api.NotificationRequest;
import ar.edu.utn.frc.siga.notification.api.NotificationResult;
import ar.edu.utn.frc.siga.notification.api.NotificationSender;
import ar.edu.utn.frc.siga.notification.api.NotificationTemplate;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;
import java.util.Map;

@SpringBootTest
@ActiveProfiles("integration")
@Testcontainers(disabledWithoutDocker = true)
class ManualEmailSendTest {

    @Autowired
    private NotificationSender notificationSender;

    @Test
    void sendRealEmail() throws InterruptedException {
        NotificationResult result = notificationSender.send(new NotificationRequest(
                NotificationTemplate.ROOM_REQUEST_RESOLVED,
                List.of(new NotificationRecipient("Vos", "TU_EMAIL_REAL@gmail.com")),
                Map.of(
                        "tituloBanner", "Aula confirmada",
                        "docente", "Vos",
                        "parrafoConfirmacion", "Confirmamos el aula para tu pedido de prueba #999.",
                        "aulasLabel", "Aula",
                        "aulas", List.of("Aula de prueba (Edificio de prueba)")),
                "manual-test-" + System.currentTimeMillis()));

        System.out.println("queued=" + result.queued() + " skipped=" + result.skipped());
        Thread.sleep(5000);
    }
}
