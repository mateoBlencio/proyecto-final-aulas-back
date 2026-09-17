package ar.edu.utn.frc.siga.space;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ar.edu.utn.frc.siga.AbstractIntegrationTest;
import ar.edu.utn.frc.siga.auth.model.SystemRole;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@DisplayName("ClassroomType ABM (integración)")
class ClassroomTypeApiIntegrationTest extends AbstractIntegrationTest {

    private MockMvc auxiliarMockMvc;

    @BeforeEach
    void setUpAuxiliar() {
        auxiliarMockMvc = mockMvcAs("auxiliar@frc.utn.edu.ar", SystemRole.AUXILIAR_AULICO);
    }

    private String body(String description) {
        return """
                {"description": "%s"}
                """.formatted(description);
    }

    private long create(String description) throws Exception {
        String response = mockMvc.perform(post("/v1/classroom-types")
                        .contentType(MediaType.APPLICATION_JSON).content(body(description)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.parse(response).read("$.id", Long.class);
    }

    @Test
    @DisplayName("POST crea, GET /{id} lo devuelve")
    void create_thenFetchById() throws Exception {
        String description = "Laboratorio " + System.nanoTime();
        long id = create(description);

        mockMvc.perform(get("/v1/classroom-types/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.description").value(description))
                .andExpect(jsonPath("$.enabled").value(true));
    }

    @Test
    @DisplayName("PUT cambia la descripción")
    void update_changesDescription() throws Exception {
        long id = create("Aula magna " + System.nanoTime());
        String renamed = "Aula magna editada " + System.nanoTime();

        mockMvc.perform(put("/v1/classroom-types/" + id)
                        .contentType(MediaType.APPLICATION_JSON).content(body(renamed)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.description").value(renamed));
    }

    @Test
    @DisplayName("POST con descripción duplicada responde 400")
    void create_duplicate_returns400() throws Exception {
        String description = "Duplicado " + System.nanoTime();
        create(description);

        mockMvc.perform(post("/v1/classroom-types")
                        .contentType(MediaType.APPLICATION_JSON).content(body(description)))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON));
    }

    @Test
    @DisplayName("GET /{id} inexistente responde 404")
    void getById_missing_returns404() throws Exception {
        mockMvc.perform(get("/v1/classroom-types/999999999"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("AUXILIAR_AULICO puede leer pero no crear")
    void auxiliar_readsButCannotWrite() throws Exception {
        auxiliarMockMvc.perform(get("/v1/classroom-types"))
                .andExpect(status().isOk());
        auxiliarMockMvc.perform(post("/v1/classroom-types")
                        .contentType(MediaType.APPLICATION_JSON).content(body("X " + System.nanoTime())))
                .andExpect(status().isForbidden());
    }
}
