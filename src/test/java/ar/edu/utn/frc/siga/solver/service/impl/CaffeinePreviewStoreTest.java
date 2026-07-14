package ar.edu.utn.frc.siga.solver.service.impl;

import ar.edu.utn.frc.siga.solver.config.SolverProperties;
import ar.edu.utn.frc.siga.solver.model.SolverAllocation;
import ar.edu.utn.frc.siga.solver.model.SolverPreview;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("CaffeinePreviewStore")
class CaffeinePreviewStoreTest {

    private CaffeinePreviewStore store;

    @BeforeEach
    void setUp() {
        store = new CaffeinePreviewStore(new SolverProperties());
    }

    @Test
    @DisplayName("save luego get devuelve la preview guardada; tras remove, get queda vacío")
    void saveGetRemove() {
        SolverPreview preview = new SolverPreview("prev_test", List.of(new SolverAllocation("1", 5)));

        store.save(preview);
        assertThat(store.get("prev_test")).isPresent().contains(preview);

        store.remove("prev_test");
        assertThat(store.get("prev_test")).isEmpty();
    }
}
