package ar.edu.utn.frc.siga.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ar.edu.utn.frc.siga.AbstractIntegrationTest;
import ar.edu.utn.frc.siga.auth.model.Role;
import ar.edu.utn.frc.siga.auth.model.User;
import ar.edu.utn.frc.siga.auth.repository.UserRepository;
import ar.edu.utn.frc.siga.auth.security.JwtService;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import tools.jackson.databind.ObjectMapper;

@DisplayName("User API (integración)")
class UserApiIntegrationTest extends AbstractIntegrationTest {

    private static final AtomicLong SEQ = new AtomicLong();

    @Autowired
    private WebApplicationContext webApplicationContext;
    @Autowired
    private JwtService jwtService;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private ObjectMapper objectMapper;

    private static String uniqueEmail() {
        return "user" + SEQ.incrementAndGet() + "." + System.nanoTime() + "@frc.utn.edu.ar";
    }

    private String createBody(String email, String password, Role rol) {
        return """
                {"email":"%s","password":"%s","rol":"%s"}
                """.formatted(email, password, rol.name());
    }

    /** MockMvc que autentica por defecto con un token del rol indicado. */
    private MockMvc mockMvcAs(String email, Role role) {
        String token = jwtService.generateAccessToken(email, Set.of(role));
        return MockMvcBuilders.webAppContextSetup(webApplicationContext)
                .apply(springSecurity())
                .defaultRequest(get("/").header("Authorization", "Bearer " + token))
                .build();
    }

    /** Crea un usuario vía API (SUBSECRETARIA) y devuelve su id. */
    private int createUser(String email, Role rol) throws Exception {
        MvcResult result = mockMvc.perform(post("/v1/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody(email, "supersegura", rol)))
                .andExpect(status().isCreated())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asInt();
    }

    // ---- create ----

    @Test
    @DisplayName("POST /v1/users crea el usuario (201), hashea la contraseña y no la expone")
    void create_persistsUser_withoutExposingPassword() throws Exception {
        String email = uniqueEmail();

        MvcResult result = mockMvc.perform(post("/v1/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody(email, "supersegura", Role.AUXILIAR_AULICO)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value(email))
                .andExpect(jsonPath("$.rol").value("AUXILIAR_AULICO"))
                .andExpect(jsonPath("$.password").doesNotExist())
                .andReturn();

        int id = objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asInt();
        User saved = userRepository.findById(id).orElseThrow();
        assertThat(saved.getEnabled()).isTrue();
        assertThat(saved.getPasswordHash()).isNotEqualTo("supersegura");
        assertThat(saved.getRoles()).containsExactly(Role.AUXILIAR_AULICO);
    }

    @Test
    @DisplayName("POST /v1/users con email duplicado responde 400 ProblemDetail")
    void create_duplicateEmail_returns400() throws Exception {
        String email = uniqueEmail();
        createUser(email, Role.AUXILIAR_AULICO);

        mockMvc.perform(post("/v1/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody(email, "supersegura", Role.AUXILIAR_AULICO)))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.title").value("User domain error"));
    }

    @Test
    @DisplayName("POST /v1/users con dominio no institucional responde 400")
    void create_nonInstitutionalDomain_returns400() throws Exception {
        mockMvc.perform(post("/v1/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody("ajeno" + System.nanoTime() + "@gmail.com", "supersegura",
                                Role.AUXILIAR_AULICO)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("User domain error"));
    }

    @Test
    @DisplayName("POST /v1/users con body inválido responde 400 con errores de validación")
    void create_invalidBody_returns400WithErrors() throws Exception {
        // password de menos de 8 caracteres → falla @Size
        mockMvc.perform(post("/v1/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody(uniqueEmail(), "corta", Role.AUXILIAR_AULICO)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.password").exists());
    }

    // ---- enabled / disabled ----

    @Test
    @DisplayName("PATCH /v1/users/{id}/enabled alterna el estado y lo mueve entre listados")
    void setEnabled_togglesStateAndListing() throws Exception {
        String email = uniqueEmail();
        int id = createUser(email, Role.AUXILIAR_AULICO);

        mockMvc.perform(patch("/v1/users/{id}/enabled", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"enabled\":false}"))
                .andExpect(status().isOk());

        assertThat(userRepository.findById(id).orElseThrow().getEnabled()).isFalse();
    }

    // ---- changeRole ----

    @Test
    @DisplayName("PATCH /v1/users/{id}/role cambia el rol")
    void changeRole_updatesRole() throws Exception {
        int id = createUser(uniqueEmail(), Role.AUXILIAR_AULICO);

        mockMvc.perform(patch("/v1/users/{id}/role", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"rol\":\"SUBSECRETARIA\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rol").value("SUBSECRETARIA"));

        assertThat(userRepository.findById(id).orElseThrow().getRoles())
                .containsExactly(Role.SUBSECRETARIA);
    }

    @Test
    @DisplayName("PATCH /v1/users/{id}/role rechaza (400) editar el propio rol")
    void changeRole_ownRole_returns400() throws Exception {
        String email = uniqueEmail();
        int id = createUser(email, Role.SUBSECRETARIA);

        // Autenticado como el mismo usuario que se intenta editar.
        mockMvcAs(email, Role.SUBSECRETARIA).perform(patch("/v1/users/{id}/role", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"rol\":\"AUXILIAR_AULICO\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("User domain error"));
    }

    // ---- listados ----

    @Test
    @DisplayName("GET /v1/users devuelve una página con los campos de paginación")
    void findEnabled_returnsPage() throws Exception {
        createUser(uniqueEmail(), Role.AUXILIAR_AULICO);

        mockMvc.perform(get("/v1/users"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.totalElements").exists())
                .andExpect(jsonPath("$.totalPages").exists());
    }

    // ---- autorización por rol ----

    @Test
    @DisplayName("GET /v1/users es accesible para AUXILIAR_AULICO (200)")
    void findEnabled_asAuxiliar_returns200() throws Exception {
        mockMvcAs("aux." + System.nanoTime() + "@frc.utn.edu.ar", Role.AUXILIAR_AULICO)
                .perform(get("/v1/users"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("GET /v1/users/disabled está prohibido para AUXILIAR_AULICO (403)")
    void findDisabled_asAuxiliar_returns403() throws Exception {
        mockMvcAs("aux." + System.nanoTime() + "@frc.utn.edu.ar", Role.AUXILIAR_AULICO)
                .perform(get("/v1/users/disabled"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("POST /v1/users está prohibido para AUXILIAR_AULICO (403)")
    void create_asAuxiliar_returns403() throws Exception {
        mockMvcAs("aux." + System.nanoTime() + "@frc.utn.edu.ar", Role.AUXILIAR_AULICO)
                .perform(post("/v1/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody(uniqueEmail(), "supersegura", Role.AUXILIAR_AULICO)))
                .andExpect(status().isForbidden());
    }
}
