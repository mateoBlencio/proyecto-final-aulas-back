package ar.edu.utn.frc.siga.academic.sync;

import ar.edu.utn.frc.siga.sysacad.api.SysacadSyncStateService;
import ar.edu.utn.frc.siga.sysacad.api.SysacadView;
import ar.edu.utn.frc.siga.sysacad.api.SysacadViewSyncer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "siga.sysacad", name = "enabled", havingValue = "true")
public class CommissionSyncService implements SysacadViewSyncer {

    static final String UNRESOLVED_PERIOD =
            "Sync de Comisiones no implementado: la vista no publica el cuatrimestre, "
                    + "no se puede resolver el AcademicPeriod de la clave natural de comision";

    private final SysacadSyncStateService syncStateService;

    @Override
    public SysacadView view() {
        return SysacadView.COMISIONES;
    }

    @Override
    public void sync() {
        log.warn(UNRESOLVED_PERIOD);
        syncStateService.recordFailure(SysacadView.COMISIONES, UNRESOLVED_PERIOD);
    }
}
