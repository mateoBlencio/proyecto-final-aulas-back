package ar.edu.utn.frc.siga;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Base para tests de integración: levanta el contexto completo de Spring contra
 * un Postgres real vía Testcontainers.
 *
 * <p>Decisiones:
 * <ul>
 *     <li>{@code @Testcontainers(disabledWithoutDocker = true)} sin ningún {@code @Container}:
 *     no manejamos un contenedor propio, pero la anotación igual evalúa la disponibilidad de
 *     Docker y skipea la clase entera (no falla) antes de intentar levantar el contexto.</li>
 *     <li>El contenedor real lo crea el driver JDBC {@code jdbc:tc:...} (ver
 *     application-integration.yaml), que es singleton por JVM y se reutiliza entre clases de
 *     test — compatible con el cache de contextos de Spring, a diferencia de un
 *     {@code @Container static} o {@code @ServiceConnection} por clase.</li>
 *     <li>Sin {@code @Transactional}: Hibernate Envers necesita que los commits ocurran de
 *     verdad para auditar; un rollback automático por test rompería esa auditoría.</li>
 * </ul>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@ActiveProfiles("integration")
@Testcontainers(disabledWithoutDocker = true)
public abstract class AbstractIntegrationTest {

    @Autowired
    protected MockMvc mockMvc;
}
