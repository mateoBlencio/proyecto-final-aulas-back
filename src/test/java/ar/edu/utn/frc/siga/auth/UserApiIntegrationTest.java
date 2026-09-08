package ar.edu.utn.frc.siga.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ar.edu.utn.frc.siga.AbstractIntegrationTest;
import ar.edu.utn.frc.siga.auth.model.SystemRole;
import ar.edu.utn.frc.siga.auth.model.User;
import ar.edu.utn.frc.siga.auth.repository.UserRepository;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.ObjectMapper;

@DisplayName("User API (integración)")
class UserApiIntegrationTest extends AbstractIntegrationTest {

    private static final AtomicLong SEQ = new AtomicLong();

    @Autowired
    private UserRepository userRepository;
    @Autowired
    private ObjectMapper objectMapper;

    private static String uniqueEmail() {
        return "user" + SEQ.incrementAndGet() + "." + System.nanoTime() + "@frc.utn.edu.ar";
    }

    private String createBody(String email, String password) {
        return """
                {"email":"%s","password":"%s","firstName":"Nombre","lastName":"Apellido"}
                """.formatted(email, password);
    }

    private long createUser(String email) throws Exception {
        MvcResult result = mockMvc.perform(post("/v1/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody(email, "supersegura")))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asLong();
    }

    // ---- create ----

    @Test
    @DisplayName("POST /v1/users crea el usuario (201), hashea la contraseña y no la expone")
    void create_persistsUser_withoutExposingPassword() throws Exception {
        String email = uniqueEmail();

        MvcResult result = mockMvc.perform(post("/v1/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody(email, "supersegura")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value(email))
                .andExpect(jsonPath("$.firstName").value("Nombre"))
                .andExpect(jsonPath("$.lastName").value("Apellido"))
                .andExpect(jsonPath("$.roleAssignments").isArray())
                .andExpect(jsonPath("$.roleAssignments").isEmpty())
                .andExpect(jsonPath("$.password").doesNotExist())
                .andReturn();

        long id = objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asLong();
        User saved = userRepository.findById(id).orElseThrow();
        assertThat(saved.getEnabled()).isTrue();
        assertThat(saved.getPasswordHash()).isNotEqualTo("supersegura");
    }

    @Test
    @DisplayName("POST /v1/users con email duplicado responde 400 ProblemDetail")
    void create_duplicateEmail_returns400() throws Exception {
        String email = uniqueEmail();
        createUser(email);

        mockMvc.perform(post("/v1/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody(email, "supersegura")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("User domain error"));
    }

    @Test
    @DisplayName("POST /v1/users con dominio no institucional responde 400")
    void create_nonInstitutionalDomain_returns400() throws Exception {
        mockMvc.perform(post("/v1/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody("ajeno" + System.nanoTime() + "@gmail.com", "supersegura")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("User domain error"));
    }

    @Test
    @DisplayName("POST /v1/users con body inválido responde 400 con errores de validación")
    void create_invalidBody_returns400WithErrors() throws Exception {
        mockMvc.perform(post("/v1/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody(uniqueEmail(), "")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.password").exists());
    }

    // ---- enabled / disabled ----

    @Test
    @DisplayName("PATCH /v1/users/{id}/enabled alterna el estado y lo mueve entre listados")
    void setEnabled_togglesStateAndListing() throws Exception {
        String email = uniqueEmail();
        long id = createUser(email);

        mockMvc.perform(patch("/v1/users/{id}/enabled", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"enabled\":false}"))
                .andExpect(status().isOk());

        assertThat(userRepository.findById(id).orElseThrow().getEnabled()).isFalse();
    }

    // ---- listados ----

    @Test
    @DisplayName("GET /v1/users devuelve una página con los campos de paginación")
    void findEnabled_returnsPage() throws Exception {
        createUser(uniqueEmail());

        mockMvc.perform(get("/v1/users"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.page.totalElements").exists())
                .andExpect(jsonPath("$.page.totalPages").exists());
    }

    // ---- autorización por rol ----

    @Test
    @DisplayName("GET /v1/users es accesible para AUXILIAR_AULICO (200)")
    void findEnabled_asAuxiliar_returns200() throws Exception {
        mockMvcAs("aux." + System.nanoTime() + "@frc.utn.edu.ar", SystemRole.AUXILIAR_AULICO)
                .perform(get("/v1/users"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("GET /v1/users/disabled está prohibido para AUXILIAR_AULICO (403)")
    void findDisabled_asAuxiliar_returns403() throws Exception {
        mockMvcAs("aux." + System.nanoTime() + "@frc.utn.edu.ar", SystemRole.AUXILIAR_AULICO)
                .perform(get("/v1/users/disabled"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("POST /v1/users está prohibido para AUXILIAR_AULICO (403)")
    void create_asAuxiliar_returns403() throws Exception {
        mockMvcAs("aux." + System.nanoTime() + "@frc.utn.edu.ar", SystemRole.AUXILIAR_AULICO)
                .perform(post("/v1/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody(uniqueEmail(), "supersegura")))
                .andExpect(status().isForbidden());
    }
}
