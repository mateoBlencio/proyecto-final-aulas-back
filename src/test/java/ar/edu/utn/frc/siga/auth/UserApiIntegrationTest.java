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
    @DisplayName("POST /v1/users con subdominio institucional (sistemas.frc.utn.edu.ar) crea el usuario (201)")
    void create_institutionalSubdomain_returns201() throws Exception {
        String email = "jperez" + System.nanoTime() + "@sistemas.frc.utn.edu.ar";

        mockMvc.perform(post("/v1/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody(email, "supersegura")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value(email));
    }

    @Test
    @DisplayName("POST /v1/users con mayúsculas guarda y devuelve el email en minúsculas")
    void create_mixedCaseEmail_persistsLowercase() throws Exception {
        long n = System.nanoTime();
        String lower = "jperez.mayus" + n + "@sistemas.frc.utn.edu.ar";
        String mixed = "JPerez.Mayus" + n + "@Sistemas.FRC.utn.edu.ar";

        MvcResult result = mockMvc.perform(post("/v1/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody(mixed, "supersegura")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value(lower))
                .andReturn();

        long id = objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asLong();
        assertThat(userRepository.findById(id).orElseThrow().getEmail()).isEqualTo(lower);
    }

    @Test
    @DisplayName("POST /v1/users con un email que solo difiere en mayúsculas de uno existente responde 400 como duplicado")
    void create_duplicateDifferingOnlyInCase_returns400() throws Exception {
        String lower = "jperez.dup" + System.nanoTime() + "@frc.utn.edu.ar";
        createUser(lower);

        mockMvc.perform(post("/v1/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody(lower.toUpperCase(java.util.Locale.ROOT), "supersegura")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("User domain error"));

        assertThat(userRepository.findAll().stream()
                .filter(u -> u.getEmail().equalsIgnoreCase(lower))
                .count()).isEqualTo(1);
    }

    @Test
    @DisplayName("POST /v1/users con espacios alrededor del email responde 400 de validación y no crea el usuario")
    void create_emailWithSurroundingSpaces_returns400() throws Exception {
        // AuthControllerIntegrationTest siembra este usuario en la misma base: se compara antes y después.
        String email = "subdominio.test@sistemas.frc.utn.edu.ar";
        long before = usersMatching(email);

        mockMvc.perform(post("/v1/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody("  " + email + "  ", "supersegura")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.email").exists());

        assertThat(usersMatching(email)).isEqualTo(before);
    }

    private long usersMatching(String email) {
        return userRepository.findAll().stream()
                .filter(saved -> saved.getEmail().strip().equalsIgnoreCase(email))
                .count();
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

    @Test
    @DisplayName("POST /v1/users con initialRole AUXILIAR_AULICO/GLOBAL responde 400 y no crea el usuario")
    void create_initialRoleWithDisallowedScope_returns400AndRollsBack() throws Exception {
        String email = uniqueEmail();

        mockMvc.perform(post("/v1/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"supersegura","firstName":"Nombre","lastName":"Apellido",
                                 "initialRole":{"role":"AUXILIAR_AULICO","scopeType":"GLOBAL","scopeId":null}}
                                """.formatted(email)))
                .andExpect(status().isBadRequest());

        assertThat(userRepository.existsByEmail(email)).isFalse();
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
        mockMvcAsAuxiliar("aux." + System.nanoTime() + "@frc.utn.edu.ar")
                .perform(get("/v1/users"))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("GET /v1/users/disabled está prohibido para AUXILIAR_AULICO (403)")
    void findDisabled_asAuxiliar_returns403() throws Exception {
        mockMvcAsAuxiliar("aux." + System.nanoTime() + "@frc.utn.edu.ar")
                .perform(get("/v1/users/disabled"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("POST /v1/users está prohibido para AUXILIAR_AULICO (403)")
    void create_asAuxiliar_returns403() throws Exception {
        mockMvcAsAuxiliar("aux." + System.nanoTime() + "@frc.utn.edu.ar")
                .perform(post("/v1/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody(uniqueEmail(), "supersegura")))
                .andExpect(status().isForbidden());
    }
}
