package ar.edu.utn.frc.siga.academic.sync;

import ar.edu.utn.frc.siga.sysacad.api.SysacadSyncStateService;
import ar.edu.utn.frc.siga.sysacad.api.SysacadView;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("CommissionSyncService")
class CommissionSyncServiceTest {

    @Mock
    private SysacadSyncStateService syncStateService;

    @Test
    @DisplayName("sync: registra el skip porque la vista no permite resolver el AcademicPeriod")
    void syncRecordsSkip() {
        new CommissionSyncService(syncStateService).sync();

        verify(syncStateService).recordFailure(SysacadView.COMISIONES, CommissionSyncService.UNRESOLVED_PERIOD);
    }
}
