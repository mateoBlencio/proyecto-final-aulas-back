package ar.edu.utn.frc.classroom_allocation.solver.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Contact;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.servers.Server;
import org.springframework.context.annotation.Configuration;

@Configuration
@OpenAPIDefinition(
        info = @Info(
                title = "Classroom Allocation API",
                version = "1.0",
                description = "API para asignación automática de aulas mediante optimización con Timefold Solver.",
                contact = @Contact(name = "Proyecto Final")
        ),
        servers = @Server(url = "/", description = "Local")
)
public class OpenApiConfig {
}