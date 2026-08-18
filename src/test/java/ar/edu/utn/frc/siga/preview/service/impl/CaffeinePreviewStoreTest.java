package ar.edu.utn.frc.siga.preview.service.impl;

import ar.edu.utn.frc.siga.preview.config.PreviewSettings;
import ar.edu.utn.frc.siga.optimizer.model.OptimizerAllocation;
import ar.edu.utn.frc.siga.optimizer.model.OptimizationResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@DisplayName("CaffeinePreviewStore")
class CaffeinePreviewStoreTest {

    private CaffeinePreviewStore store;

    @BeforeEach
    void setUp() {
        PreviewSettings previewSettings = mock(PreviewSettings.class);
        when(previewSettings.getTtlMinutes()).thenReturn(30L);
        store = new CaffeinePreviewStore(previewSettings);
        store.init();
    }

    @Test
    @DisplayName("save luego get devuelve la preview guardada; tras remove, get queda vacío")
    void saveGetRemove() {
        OptimizationResult preview = new OptimizationResult("prev_test", List.of(new OptimizerAllocation("1", 5)));

        store.save(preview);
        assertThat(store.get("prev_test")).isPresent().contains(preview);

        store.remove("prev_test");
        assertThat(store.get("prev_test")).isEmpty();
    }
}
