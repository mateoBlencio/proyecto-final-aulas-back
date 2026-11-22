package ar.edu.utn.frc.siga.allocation;

import ar.edu.utn.frc.siga.AbstractIntegrationTest;
import ar.edu.utn.frc.siga.auth.model.SystemRole;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.util.stream.Stream;

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("Endpoints de escritura de allocation (integración): autorización")
class AllocationWriteAuthorizationIntegrationTest extends AbstractIntegrationTest {

    private static final String ASSIGN_BODY = "{\"items\":[{\"occurrenceIds\":[1],\"classroomId\":1}]}";
    private static final String DEALLOCATE_BODY = "{\"items\":[{\"occurrenceIds\":[1]}]}";
    private static final String RELEASE_BODY = "{\"items\":[{\"occurrenceIds\":[1]}],\"reason\":\"CANCELLED\"}";

    @Autowired
    private WebApplicationContext webApplicationContext;

    // Bodies pass Bean Validation: @Valid runs before @PreAuthorize and an invalid body would answer 400.
    static Stream<Arguments> writeEndpoints() {
        return Stream.of(
                Arguments.of(HttpMethod.POST, "/v1/allocations", ASSIGN_BODY),
                Arguments.of(HttpMethod.PUT, "/v1/allocations", ASSIGN_BODY),
                Arguments.of(HttpMethod.POST, "/v1/allocations/impact", ASSIGN_BODY),
                Arguments.of(HttpMethod.DELETE, "/v1/allocations", DEALLOCATE_BODY),
                Arguments.of(HttpMethod.POST, "/v1/allocations/releases", RELEASE_BODY),
                Arguments.of(HttpMethod.DELETE, "/v1/allocations/releases", DEALLOCATE_BODY));
    }

    @ParameterizedTest(name = "{0} {1} con rol CONSULTA responde 403")
    @MethodSource("writeEndpoints")
    @DisplayName("un usuario CONSULTA no puede escribir asignaciones")
    void consultaRoleGets403(HttpMethod method, String uri, String body) throws Exception {
        MockMvc consulta = mockMvcAs("consulta-allocation@frc.utn.edu.ar", SystemRole.CONSULTA);

        consulta.perform(request(method, uri).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isForbidden());
    }

    @ParameterizedTest(name = "{0} {1} sin token responde 401")
    @MethodSource("writeEndpoints")
    @DisplayName("sin autenticar no se puede escribir asignaciones")
    void anonymousGets401(HttpMethod method, String uri, String body) throws Exception {
        MockMvc anonymous = MockMvcBuilders.webAppContextSetup(webApplicationContext)
                .apply(springSecurity()).build();

        anonymous.perform(request(method, uri).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isUnauthorized());
    }
}
