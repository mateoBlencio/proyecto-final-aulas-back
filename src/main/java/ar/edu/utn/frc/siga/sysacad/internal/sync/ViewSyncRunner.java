package ar.edu.utn.frc.siga.sysacad.internal.sync;

import ar.edu.utn.frc.siga.sysacad.api.SysacadSyncStateService;
import ar.edu.utn.frc.siga.sysacad.api.SysacadView;
import java.util.function.IntSupplier;
import org.slf4j.Logger;

final class ViewSyncRunner {

    private ViewSyncRunner() {
    }

    static void run(SysacadSyncStateService syncStateService, SysacadView view, String label, Logger log,
                    IntSupplier body) {
        try {
            int affected = body.getAsInt();
            syncStateService.recordSuccess(view, affected);
            log.info("Sync de {} finalizado: {} filas afectadas", label, affected);
        } catch (RuntimeException e) {
            syncStateService.recordFailure(view, e.getMessage());
            throw e;
        }
    }
}
