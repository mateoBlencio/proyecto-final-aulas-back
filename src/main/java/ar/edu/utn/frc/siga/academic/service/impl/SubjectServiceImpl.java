package ar.edu.utn.frc.siga.academic.service.impl;

import ar.edu.utn.frc.siga.academic.dto.response.SubjectResponseDto;
import ar.edu.utn.frc.siga.academic.mapper.SubjectMapper;
import ar.edu.utn.frc.siga.academic.model.Specialty;
import ar.edu.utn.frc.siga.academic.model.StudyPlan;
import ar.edu.utn.frc.siga.academic.model.Subject;
import ar.edu.utn.frc.siga.academic.repository.SpecialtyRepository;
import ar.edu.utn.frc.siga.academic.repository.StudyPlanRepository;
import ar.edu.utn.frc.siga.academic.repository.SubjectRepository;
import ar.edu.utn.frc.siga.academic.service.SubjectService;
import ar.edu.utn.frc.siga.academic.service.command.SubjectSyncCommand;
import ar.edu.utn.frc.siga.common.exception.ResourceNotFoundException;
import ar.edu.utn.frc.siga.common.util.Finder;
import ar.edu.utn.frc.siga.common.util.Hashes;
import ar.edu.utn.frc.siga.common.util.Maps;
import java.time.Instant;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class SubjectServiceImpl implements SubjectService {

    private final SubjectRepository subjectRepository;
    private final StudyPlanRepository studyPlanRepository;
    private final SpecialtyRepository specialtyRepository;
    private final SubjectMapper subjectMapper;
    private final StudyPlanResolver studyPlanResolver;

    @Override
    public List<SubjectResponseDto> findAll() {
        return subjectRepository.findAllActive().stream()
                .map(subjectMapper::toDto)
                .toList();
    }

    @Override
    public SubjectResponseDto findById(Long id) {
        return subjectMapper.toDto(Finder.orThrow(subjectRepository::findActiveById, id, "Subject"));
    }

    @Override
    @Transactional
    public void activate(Long id) {
        subjectRepository.restore(Finder.orThrow(subjectRepository::findById, id, "Subject"));
    }

    @Override
    @Transactional
    public void deactivate(Long id) {
        subjectRepository.softDelete(Finder.orThrow(subjectRepository::findById, id, "Subject"));
    }

    @Override
    public List<SubjectResponseDto> findByIds(Collection<Long> ids) {
        return subjectRepository.findAllById(ids).stream()
                .filter(Subject::isActive)
                .map(subjectMapper::toDto)
                .toList();
    }

    @Override
    public SubjectResponseDto findByCodeAndStudyPlan(Integer code, Integer studyPlanCode, Integer specialtyCode) {
        StudyPlan studyPlan = requireStudyPlan(studyPlanCode, specialtyCode);
        return subjectMapper.toDto(subjectRepository.findByCodeAndStudyPlanAndDeletedAtIsNull(code, studyPlan)
                .orElseThrow(() -> ResourceNotFoundException.of("Subject", code)));
    }

    @Override
    public List<SubjectResponseDto> findBySpecialtyCode(Integer specialtyCode) {
        return subjectRepository.findByStudyPlan_Specialty_SpecialtyCodeAndDeletedAtIsNull(specialtyCode).stream()
                .map(subjectMapper::toDto)
                .toList();
    }

    private StudyPlan requireStudyPlan(Integer studyPlanCode, Integer specialtyCode) {
        Specialty specialty = Finder.orThrow(specialtyRepository::findBySpecialtyCode, specialtyCode, "Specialty");
        return studyPlanRepository.findByPlanCodeAndSpecialtyAndDeletedAtIsNull(studyPlanCode, specialty)
                .orElseThrow(() -> ResourceNotFoundException.of("StudyPlan", studyPlanCode));
    }

    @Override
    @Transactional
    public int syncSubjects(List<SubjectSyncCommand> commands) {
        Instant syncedAt = Instant.now();
        // Sync/reconciliación: findAll() a propósito (ve filas borradas) para poder re-activarlas en vez de duplicar.
        Map<SubjectKey, Subject> existing = Maps.byId(subjectRepository.findAll(), SubjectKey::of);
        Map<StudyPlanKey, Optional<StudyPlan>> studyPlansByKey = new HashMap<>();
        int affected = 0;

        for (SubjectSyncCommand command : commands) {
            if (command.specialtyCode() == null || command.studyPlanCode() == null
                    || command.subjectCode() == null || command.name() == null) {
                log.warn("Materia de SysAcad ignorada por datos incompletos: especialidad={}, plan={}, materia={}",
                        command.specialtyCode(), command.studyPlanCode(), command.subjectCode());
                continue;
            }
            StudyPlanKey key = new StudyPlanKey(command.specialtyCode(), command.studyPlanCode());
            Optional<StudyPlan> studyPlan = studyPlansByKey.computeIfAbsent(key,
                    k -> studyPlanResolver.findOrCreate(command.specialtyCode(), command.studyPlanCode(), syncedAt));
            if (studyPlan.isEmpty()) {
                log.warn("No se pudo resolver la especialidad {} para la materia {}",
                        command.specialtyCode(), command.subjectCode());
                continue;
            }

            String hash = Hashes.sha256Hex(command.name(), command.term());
            Subject subject = existing.get(new SubjectKey(command.subjectCode(), studyPlan.get().getId()));

            if (subject == null) {
                subjectRepository.save(Subject.builder()
                        .code(command.subjectCode())
                        .name(command.name())
                        .term(command.term())
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
            subject.setName(command.name());
            subject.setTerm(command.term());
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
