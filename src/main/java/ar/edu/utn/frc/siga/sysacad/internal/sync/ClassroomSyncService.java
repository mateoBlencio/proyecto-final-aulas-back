package ar.edu.utn.frc.siga.sysacad.internal.sync;

import ar.edu.utn.frc.siga.space.service.ClassroomService;
import ar.edu.utn.frc.siga.space.service.command.ClassroomSyncCommand;
import ar.edu.utn.frc.siga.sysacad.api.SysacadCatalogReader;
import ar.edu.utn.frc.siga.sysacad.api.SysacadClassroomDto;
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
public class ClassroomSyncService implements SysacadViewSyncer {

    private final ClassroomService classroomService;
    private final SysacadSyncStateService syncStateService;

    @Override
    public SysacadView view() {
        return SysacadView.AULAS;
    }

    @Override
    public void sync(SysacadCatalogReader catalog) {
        ViewSyncRunner.run(syncStateService, SysacadView.AULAS, "Aulas", log, () -> {
            List<ClassroomSyncCommand> commands = catalog.findClassrooms().stream()
                    .map(ClassroomSyncService::toCommand)
                    .toList();
            return classroomService.syncClassrooms(commands);
        });
    }

    private static ClassroomSyncCommand toCommand(SysacadClassroomDto dto) {
        return new ClassroomSyncCommand(dto.roomNumber(), dto.buildingCode(), dto.isEnabled(), dto.capacity());
    }
}
