package ar.edu.utn.frc.siga;

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import ar.edu.utn.frc.siga.auth.model.Role;
import ar.edu.utn.frc.siga.auth.security.JwtService;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
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
 *     <li>Seguridad: el profile de integración mantiene la cadena JWT activa (ver SecurityConfig).
 *     En vez de desactivarla, el MockMvc se arma con {@code springSecurity()} y firma un access
 *     token real (rol SUBSECRETARIA, con acceso a todos los endpoints) que viaja como header
 *     {@code Authorization} por defecto en cada request. Así los tests de API ejercitan el filtro
 *     JWT de verdad sin hacer login por HTTP.</li>
 * </ul>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@ActiveProfiles("integration")
@Testcontainers(disabledWithoutDocker = true)
public abstract class AbstractIntegrationTest {

    @Autowired
    private WebApplicationContext webApplicationContext;

    @Autowired
    private JwtService jwtService;

    protected MockMvc mockMvc;

    @BeforeEach
    void setUpAuthenticatedMockMvc() {
        String token = jwtService.generateAccessToken(
                "integration-test@frc.utn.edu.ar", Set.of(Role.SUBSECRETARIA));

        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext)
                .apply(springSecurity())
                .defaultRequest(get("/").header("Authorization", "Bearer " + token))
                .build();
    }
}