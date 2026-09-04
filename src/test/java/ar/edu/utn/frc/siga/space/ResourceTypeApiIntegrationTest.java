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
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@DisplayName("ResourceType ABM (integración)")
class ResourceTypeApiIntegrationTest extends AbstractIntegrationTest {

    private MockMvc auxiliarMockMvc;

    @BeforeEach
    void setUpAuxiliar() {
        auxiliarMockMvc = mockMvcAs("auxiliar@frc.utn.edu.ar", SystemRole.AUXILIAR_AULICO);
    }

    private String body(String name, String valueKind) {
        return """
                {"name": "%s", "valueKind": "%s"}
                """.formatted(name, valueKind);
    }

    private long create(String name, String valueKind) throws Exception {
        String response = mockMvc.perform(post("/v1/resource-types")
                        .contentType(MediaType.APPLICATION_JSON).content(body(name, valueKind)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.parse(response).read("$.id", Long.class);
    }

    @Test
    @DisplayName("POST crea, GET /{id} lo devuelve y aparece en el listado")
    void create_thenFetch() throws Exception {
        String name = "Pizarrón digital " + System.nanoTime();

        long id = create(name, "BOOLEAN");

        mockMvc.perform(get("/v1/resource-types/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value(name))
                .andExpect(jsonPath("$.valueKind").value("BOOLEAN"))
                .andExpect(jsonPath("$.enabled").value(true));

        mockMvc.perform(get("/v1/resource-types").param("size", "200"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[?(@.id == " + id + ")].name").value(Matchers.hasItem(name)));
    }

    @Test
    @DisplayName("PUT actualiza nombre y valueKind")
    void update_changesFields() throws Exception {
        String name = "Recurso " + System.nanoTime();
        long id = create(name, "COUNT");

        String renamed = name + " editado";
        mockMvc.perform(put("/v1/resource-types/" + id)
                        .contentType(MediaType.APPLICATION_JSON).content(body(renamed, "BOOLEAN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value(renamed))
                .andExpect(jsonPath("$.valueKind").value("BOOLEAN"));
    }

    @Test
    @DisplayName("POST con nombre duplicado responde 400")
    void create_duplicateName_returns400() throws Exception {
        String name = "Duplicado " + System.nanoTime();
        create(name, "COUNT");

        mockMvc.perform(post("/v1/resource-types")
                        .contentType(MediaType.APPLICATION_JSON).content(body(name, "BOOLEAN")))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON));
    }

    @Test
    @DisplayName("GET /{id} inexistente responde 404")
    void getById_missing_returns404() throws Exception {
        mockMvc.perform(get("/v1/resource-types/999999999"))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("AUXILIAR_AULICO puede leer pero no crear")
    void auxiliar_readsButCannotWrite() throws Exception {
        auxiliarMockMvc.perform(get("/v1/resource-types"))
                .andExpect(status().isOk());
        auxiliarMockMvc.perform(post("/v1/resource-types")
                        .contentType(MediaType.APPLICATION_JSON).content(body("X " + System.nanoTime(), "COUNT")))
                .andExpect(status().isForbidden());
    }
}
