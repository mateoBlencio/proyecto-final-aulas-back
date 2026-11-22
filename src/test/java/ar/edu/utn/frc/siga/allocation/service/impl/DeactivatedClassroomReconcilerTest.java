package ar.edu.utn.frc.siga.allocation.service.impl;

import ar.edu.utn.frc.siga.common.model.UnavailableReason;
import ar.edu.utn.frc.siga.space.dto.response.ClassroomResponseDto;
import ar.edu.utn.frc.siga.space.service.ClassroomService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("DeactivatedClassroomReconciler")
class DeactivatedClassroomReconcilerTest {

    @Mock
    private ClassroomService classroomService;
    @Mock
    private DeactivatedClassroomReleaser releaser;
    @Mock
    private PlatformTransactionManager transactionManager;
    @Mock
    private TransactionStatus transactionStatus;

    @Test
    @DisplayName("si la liberación falla, hace rollback y no frena el arranque")
    void fallaHaceRollbackSinPropagar() {
        when(transactionManager.getTransaction(any())).thenReturn(transactionStatus);
        when(classroomService.findDeactivatedInSiga()).thenReturn(List.of(new ClassroomResponseDto(
                204L, 204, 60, 1L, "Central", 1L, "Aula", UnavailableReason.DEACTIVATED, true)));
        when(releaser.apply(Map.of(204L, "Aula deshabilitada"))).thenThrow(new IllegalStateException("dato roto"));
        DeactivatedClassroomReconciler reconciler =
                new DeactivatedClassroomReconciler(classroomService, releaser, transactionManager);

        assertThatCode(() -> reconciler.run(new DefaultApplicationArguments())).doesNotThrowAnyException();

        verify(transactionManager).rollback(transactionStatus);
        verify(transactionManager, never()).commit(any());
    }
}
