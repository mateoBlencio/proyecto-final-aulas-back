package ar.edu.utn.frc.siga.sysacad.internal.sync;

import ar.edu.utn.frc.siga.sysacad.api.SysacadSyncStateService;
import ar.edu.utn.frc.siga.sysacad.api.SysacadView;
import ar.edu.utn.frc.siga.sysacad.internal.exception.SyncError;
import ar.edu.utn.frc.siga.sysacad.internal.exception.SysacadEmptyViewException;
import java.util.List;
import java.util.function.IntSupplier;
import org.slf4j.Logger;

final class ViewSyncRunner {

    private ViewSyncRunner() {
    }

    static <T> List<T> requireRows(List<T> rows, SysacadView view) {
        if (rows.isEmpty()) {
            throw new SysacadEmptyViewException(view.name());
        }
        return rows;
    }

    static void run(SysacadSyncStateService syncStateService, SysacadView view, String label, Logger log,
                    IntSupplier body) {
        try {
            int affected = body.getAsInt();
            syncStateService.recordSuccess(view, affected);
            log.info("Sync de {} finalizado: {} filas afectadas", label, affected);
        } catch (RuntimeException e) {
            log.error("Falló el sync de {}", label, e);
            syncStateService.recordFailure(view, SyncError.describe(e));
            throw e;
        }
    }
}
