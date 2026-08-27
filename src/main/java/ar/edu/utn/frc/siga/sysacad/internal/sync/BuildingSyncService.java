package ar.edu.utn.frc.siga.sysacad.internal.sync;

import ar.edu.utn.frc.siga.space.service.BuildingService;
import ar.edu.utn.frc.siga.space.service.command.BuildingSyncCommand;
import ar.edu.utn.frc.siga.sysacad.api.SysacadBuildingDto;
import ar.edu.utn.frc.siga.sysacad.api.SysacadCatalogReader;
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
public class BuildingSyncService implements SysacadViewSyncer {

    private final SysacadCatalogReader catalogReader;
    private final BuildingService buildingService;
    private final SysacadSyncStateService syncStateService;

    @Override
    public SysacadView view() {
        return SysacadView.EDIFICIOS;
    }

    @Override
    public void sync() {
        try {
            List<BuildingSyncCommand> commands = catalogReader.findBuildings().stream()
                    .map(BuildingSyncService::toCommand)
                    .toList();
            int affected = buildingService.syncBuildings(commands);
            syncStateService.recordSuccess(SysacadView.EDIFICIOS, affected);
            log.info("Sync de Edificios finalizado: {} filas afectadas", affected);
        } catch (RuntimeException e) {
            syncStateService.recordFailure(SysacadView.EDIFICIOS, e.getMessage());
            throw e;
        }
    }

    private static BuildingSyncCommand toCommand(SysacadBuildingDto dto) {
        return new BuildingSyncCommand(dto.buildingCode(), dto.name());
    }
}
