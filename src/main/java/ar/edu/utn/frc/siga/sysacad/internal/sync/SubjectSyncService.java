package ar.edu.utn.frc.siga.sysacad.internal.sync;

import ar.edu.utn.frc.siga.academic.service.SubjectService;
import ar.edu.utn.frc.siga.academic.service.command.SubjectSyncCommand;
import ar.edu.utn.frc.siga.sysacad.api.SysacadCatalogReader;
import ar.edu.utn.frc.siga.sysacad.api.SysacadSubjectDto;
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
public class SubjectSyncService implements SysacadViewSyncer {

    private final SysacadCatalogReader catalogReader;
    private final SubjectService subjectService;
    private final SysacadSyncStateService syncStateService;

    @Override
    public SysacadView view() {
        return SysacadView.MATERIAS;
    }

    @Override
    public void sync() {
        try {
            List<SubjectSyncCommand> commands = catalogReader.findSubjects().stream()
                    .map(SubjectSyncService::toCommand)
                    .toList();
            int affected = subjectService.syncSubjects(commands);
            syncStateService.recordSuccess(SysacadView.MATERIAS, affected);
            log.info("Sync de Materias finalizado: {} filas afectadas", affected);
        } catch (RuntimeException e) {
            syncStateService.recordFailure(SysacadView.MATERIAS, e.getMessage());
            throw e;
        }
    }

    private static SubjectSyncCommand toCommand(SysacadSubjectDto dto) {
        return new SubjectSyncCommand(dto.specialtyCode(), dto.studyPlanCode(), dto.subjectCode(), dto.name(), dto.term());
    }
}
