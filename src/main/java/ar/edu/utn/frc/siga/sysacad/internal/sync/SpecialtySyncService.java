package ar.edu.utn.frc.siga.sysacad.internal.sync;

import ar.edu.utn.frc.siga.academic.service.SpecialtyService;
import ar.edu.utn.frc.siga.academic.service.command.SpecialtySyncCommand;
import ar.edu.utn.frc.siga.sysacad.api.SysacadCatalogReader;
import ar.edu.utn.frc.siga.sysacad.api.SysacadSpecialtyDto;
import ar.edu.utn.frc.siga.sysacad.api.SysacadSyncStateService;
import ar.edu.utn.frc.siga.sysacad.api.SysacadView;
import ar.edu.utn.frc.siga.sysacad.api.SysacadViewSyncer;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "siga.sysacad", name = "enabled", havingValue = "true")
public class SpecialtySyncService implements SysacadViewSyncer {

    private final SysacadCatalogReader catalogReader;
    private final SpecialtyService specialtyService;
    private final SysacadSyncStateService syncStateService;

    @Override
    public SysacadView view() {
        return SysacadView.ESPECIALIDADES;
    }

    @Override
    public void sync() {
        try {
            List<SpecialtySyncCommand> commands = catalogReader.findSpecialties().stream()
                    .map(SpecialtySyncService::toCommand)
                    .toList();
            int affected = specialtyService.syncSpecialties(commands);
            syncStateService.recordSuccess(SysacadView.ESPECIALIDADES, affected);
            log.info("Sync de Especialidades finalizado: {} filas afectadas", affected);
        } catch (RuntimeException e) {
            syncStateService.recordFailure(SysacadView.ESPECIALIDADES, e.getMessage());
            throw e;
        }
    }

    private static SpecialtySyncCommand toCommand(SysacadSpecialtyDto dto) {
        return new SpecialtySyncCommand(dto.specialtyCode(), dto.name(), dto.abbreviation());
    }
}
