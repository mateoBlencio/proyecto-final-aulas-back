package ar.edu.utn.frc.siga.solver.service;

import ar.edu.utn.frc.siga.solver.config.SolverProperties;
import ar.edu.utn.frc.siga.solver.model.SolverPreview;
import ar.edu.utn.frc.siga.solver.service.impl.CaffeinePreviewStore;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CaffeinePreviewStoreTest {

    private final CaffeinePreviewStore store = new CaffeinePreviewStore(new SolverProperties());

    @Test
    void savedPreview_isRetrievableById() {
        store.save(new SolverPreview("prev_abc123", List.of()));

        assertThat(store.get("prev_abc123"))
                .isPresent()
                .get()
                .extracting(SolverPreview::previewId)
                .isEqualTo("prev_abc123");
    }

    @Test
    void unknownPreviewId_returnsEmpty() {
        assertThat(store.get("prev_nope")).isEmpty();
    }
}
