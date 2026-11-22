package ar.edu.utn.frc.siga.audit.internal;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.SimpleTransactionStatus;

import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("RevisionDescriptionUpdater")
class RevisionDescriptionUpdaterTest {

    private final JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
    private final PlatformTransactionManager transactionManager = mock(PlatformTransactionManager.class);
    private final RevisionDescriptionUpdater updater = new RevisionDescriptionUpdater(jdbcTemplate, transactionManager);

    @Test
    @DisplayName("actualiza revinfo por operacion_id dentro de una transacción REQUIRES_NEW")
    void updatesByOperationIdInNewTransaction() {
        when(transactionManager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());

        updater.update("op-1", "Final");

        verify(jdbcTemplate).update("UPDATE revinfo SET descripcion = ? WHERE operacion_id = ?", "Final", "op-1");
        ArgumentCaptor<TransactionDefinition> definition = ArgumentCaptor.forClass(TransactionDefinition.class);
        verify(transactionManager).getTransaction(definition.capture());
        assertThat(definition.getValue().getPropagationBehavior())
                .isEqualTo(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    @Test
    @DisplayName("si el UPDATE falla no propaga la excepción")
    void swallowsFailures() {
        when(transactionManager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
        when(jdbcTemplate.update(anyString(), any(Object[].class))).thenThrow(new IllegalStateException("db caída"));

        assertThatCode(() -> updater.update("op-1", "Final")).doesNotThrowAnyException();
    }
}
