package ar.edu.utn.frc.siga.academic.sync;

import ar.edu.utn.frc.siga.academic.model.StudyPlan;
import ar.edu.utn.frc.siga.academic.model.Subject;
import ar.edu.utn.frc.siga.academic.repository.SubjectRepository;
import ar.edu.utn.frc.siga.common.util.Hashes;
import ar.edu.utn.frc.siga.common.util.Maps;
import ar.edu.utn.frc.siga.sysacad.api.SysacadCatalogReader;
import ar.edu.utn.frc.siga.sysacad.api.SysacadSubjectDto;
import ar.edu.utn.frc.siga.sysacad.api.SysacadSyncStateService;
import ar.edu.utn.frc.siga.sysacad.api.SysacadView;
import ar.edu.utn.frc.siga.sysacad.api.SysacadViewSyncer;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "siga.sysacad", name = "enabled", havingValue = "true")
public class SubjectSyncService implements SysacadViewSyncer {

    private final SysacadCatalogReader catalogReader;
    private final SubjectRepository subjectRepository;
    private final StudyPlanResolver studyPlanResolver;
    private final SysacadSyncStateService syncStateService;

    @Override
    public SysacadView view() {
        return SysacadView.MATERIAS;
    }

    @Override
    @Transactional
    public void sync() {
        try {
            int affected = upsert(catalogReader.findSubjects());
            syncStateService.recordSuccess(SysacadView.MATERIAS, affected);
            log.info("Sync de Materias finalizado: {} filas afectadas", affected);
        } catch (RuntimeException e) {
            syncStateService.recordFailure(SysacadView.MATERIAS, e.getMessage());
            throw e;
        }
    }

    private int upsert(List<SysacadSubjectDto> rows) {
        Instant syncedAt = Instant.now();
        Map<SubjectKey, Subject> existing = Maps.byId(subjectRepository.findAll(), SubjectKey::of);
        Map<StudyPlanKey, Optional<StudyPlan>> studyPlansByKey = new HashMap<>();
        int affected = 0;

        for (SysacadSubjectDto row : rows) {
            if (row.specialtyCode() == null || row.studyPlanCode() == null
                    || row.subjectCode() == null || row.name() == null) {
                log.warn("Materia de SysAcad ignorada por datos incompletos: especialidad={}, plan={}, materia={}",
                        row.specialtyCode(), row.studyPlanCode(), row.subjectCode());
                continue;
            }
            StudyPlanKey key = new StudyPlanKey(row.specialtyCode(), row.studyPlanCode());
            Optional<StudyPlan> studyPlan = studyPlansByKey.computeIfAbsent(key,
                    k -> studyPlanResolver.findOrCreate(row.specialtyCode(), row.studyPlanCode(), syncedAt));
            if (studyPlan.isEmpty()) {
                log.warn("No se pudo resolver la especialidad {} para la materia {}", row.specialtyCode(), row.subjectCode());
                continue;
            }

            String hash = Hashes.sha256Hex(row.name(), row.term());
            Subject subject = existing.get(new SubjectKey(row.subjectCode(), studyPlan.get().getId()));

            if (subject == null) {
                subjectRepository.save(Subject.builder()
                        .code(row.subjectCode())
                        .name(row.name())
                        .term(row.term())
                        .studyPlan(studyPlan.get())
                        .syncedAt(syncedAt)
                        .sysacadHash(hash)
                        .build());
                affected++;
                continue;
            }
            if (hash.equals(subject.getSysacadHash())) {
                continue;
            }
            subject.setName(row.name());
            subject.setTerm(row.term());
            subject.setSyncedAt(syncedAt);
            subject.setSysacadHash(hash);
            subjectRepository.save(subject);
            affected++;
        }

        return affected;
    }

    private record StudyPlanKey(Integer specialtyCode, Integer planCode) {
    }
}
