package ar.edu.utn.frc.siga.sysacad.internal.sync;

import ar.edu.utn.frc.siga.academic.service.HolidayService;
import ar.edu.utn.frc.siga.academic.service.command.HolidaySyncCommand;
import ar.edu.utn.frc.siga.sysacad.api.SysacadCatalogReader;
import ar.edu.utn.frc.siga.sysacad.api.SysacadHolidayDto;
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
public class HolidaySyncService implements SysacadViewSyncer {

    private final HolidayService holidayService;
    private final SysacadSyncStateService syncStateService;

    @Override
    public SysacadView view() {
        return SysacadView.FERIADOS;
    }

    @Override
    public void sync(SysacadCatalogReader catalog) {
        ViewSyncRunner.run(syncStateService, SysacadView.FERIADOS, "Feriados", log, () -> {
            List<HolidaySyncCommand> commands = catalog.findHolidays().stream()
                    .map(HolidaySyncService::toCommand)
                    .toList();
            return holidayService.syncHolidays(commands);
        });
    }

    private static HolidaySyncCommand toCommand(SysacadHolidayDto dto) {
        return new HolidaySyncCommand(dto.date(), dto.description());
    }
}
