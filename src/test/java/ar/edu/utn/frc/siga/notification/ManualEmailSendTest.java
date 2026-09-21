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
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.util.List;
import java.util.Map;

@SpringBootTest
@ActiveProfiles("integration")
@Testcontainers(disabledWithoutDocker = true)
class ManualEmailSendTest {

    /** SMTP real de prueba: captura el mail sin mandarlo a ningún lado, se ve en su UI HTTP (puerto 8025). */
    @Container
    static final GenericContainer<?> mailhog =
            new GenericContainer<>(DockerImageName.parse("mailhog/mailhog:v1.0.1"))
                    .withExposedPorts(1025, 8025);

    @DynamicPropertySource
    static void mailProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.mail.host", mailhog::getHost);
        registry.add("spring.mail.port", () -> mailhog.getMappedPort(1025));
        registry.add("siga.notifications.email.enabled", () -> "true");
    }

    @Autowired
    private NotificationSender notificationSender;

    @Test
    void sendRealEmail() throws InterruptedException {
        NotificationResult result = notificationSender.send(new NotificationRequest(
                NotificationTemplate.ROOM_REQUEST_RESOLVED,
                List.of(new NotificationRecipient("Vos", "TU_EMAIL_REAL@gmail.com")),
                Map.of(
                        "asuntoTitulo", "Aula confirmada",
                        "asuntoPartes", List.of("Prueba manual"),
                        "tituloBanner", "Aula confirmada",
                        "docente", "Vos",
                        "parrafoConfirmacion", "Confirmamos el aula para tu pedido de prueba #999.",
                        "aulasLabel", "Aula",
                        "aulas", List.of("Aula de prueba (Edificio de prueba)")),
                "manual-test-" + System.currentTimeMillis()));

        System.out.println("queued=" + result.queued() + " skipped=" + result.skipped());
        System.out.println("UI MailHog: http://" + mailhog.getHost() + ":" + mailhog.getMappedPort(8025));
        Thread.sleep(5000);
    }
}
