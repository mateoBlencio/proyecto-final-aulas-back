package ar.edu.utn.frc.siga.academic.service.impl;

import ar.edu.utn.frc.siga.academic.dto.SubjectFilter;
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
import ar.edu.utn.frc.siga.academic.specification.SubjectSpecification;
import ar.edu.utn.frc.siga.common.exception.ResourceNotFoundException;
import ar.edu.utn.frc.siga.common.util.Finder;
import ar.edu.utn.frc.siga.common.util.Hashes;
import ar.edu.utn.frc.siga.common.util.Maps;
import java.time.Instant;
import java.util.Collection;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
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
    public Page<SubjectResponseDto> findAll(SubjectFilter filter, Pageable pageable, boolean includeDeactivated) {
        return subjectRepository.findAll(
                        SubjectSpecification.withFilter(filter)
                                .and(includeDeactivated ? Specification.<Subject>unrestricted() : SubjectSpecification.available()),
                        pageable)
                .map(subjectMapper::toDto);
    }

    @Override
    public SubjectResponseDto findById(Long id) {
        return subjectMapper.toDto(subjectRepository.findById(id)
                .filter(Subject::isAvailable)
                .orElseThrow(() -> ResourceNotFoundException.of("Subject", id)));
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
                .filter(Subject::isAvailable)
                .map(subjectMapper::toDto)
                .toList();
    }

    @Override
    public List<SubjectResponseDto> findByIdsIncludingDeactivated(Collection<Long> ids) {
        return subjectRepository.findAllById(ids).stream()
                .map(subjectMapper::toDto)
                .toList();
    }

    @Override
    public SubjectResponseDto findByCodeAndStudyPlan(Integer code, Integer studyPlanCode, Integer specialtyCode) {
        StudyPlan studyPlan = requireStudyPlan(studyPlanCode, specialtyCode);
        return subjectMapper.toDto(subjectRepository.findByCodeAndStudyPlanAndDeletedAtIsNull(code, studyPlan)
                .orElseThrow(() -> ResourceNotFoundException.of("Subject", code)));
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
        Map<SubjectKey, Subject> existing = Maps.byId(subjectRepository.findAll(), SubjectKey::of);
        Map<StudyPlanKey, Optional<StudyPlan>> studyPlansByKey = new HashMap<>();
        Map<Integer, Specialty> specialtyCache = new HashMap<>();
        int affected = 0;

        for (Map.Entry<SourceKey, List<SubjectSyncCommand>> entry : groupBySourceKey(commands).entrySet()) {
            SourceKey sourceKey = entry.getKey();
            List<SubjectSyncCommand> group = entry.getValue();
            SubjectSyncCommand command = group.get(0);
            if (hasConflictingVariants(group)) {
                log.warn("Materia de SysAcad ignorada por variantes con datos distintos: especialidad={}, plan={}, materia={}, variantes={}",
                        sourceKey.specialtyCode(), sourceKey.studyPlanCode(), sourceKey.subjectCode(),
                        group.stream().map(c -> "[nombre=" + c.name() + ", dictado=" + c.term() + "]")
                                .distinct().toList());
                continue;
            }
            if (group.size() > 1) {
                log.warn("Materia repetida en la misma corrida de SysAcad: especialidad={}, plan={}, materia={}, copias={}",
                        sourceKey.specialtyCode(), sourceKey.studyPlanCode(), sourceKey.subjectCode(), group.size());
            }
            StudyPlanKey key = new StudyPlanKey(command.specialtyCode(), command.studyPlanCode());
            StudyPlan studyPlan = studyPlansByKey.computeIfAbsent(key,
                            k -> studyPlanResolver.findOrCreate(command.specialtyCode(), command.studyPlanCode(),
                                    syncedAt, specialtyCache))
                    .orElseThrow(() -> new IllegalStateException(
                            "StudyPlanResolver devolvió vacío con especialidad y plan no nulos"));

            String hash = Hashes.sha256Hex(command.name(), command.term());
            SubjectKey subjectKey = new SubjectKey(command.subjectCode(), studyPlan.getId());
            Subject subject = existing.get(subjectKey);

            if (subject == null) {
                existing.put(subjectKey, subjectRepository.save(Subject.builder()
                        .code(command.subjectCode())
                        .name(command.name())
                        .term(command.term())
                        .studyPlan(studyPlan)
                        .syncedAt(syncedAt)
                        .sysacadHash(hash)
                        .build()));
                affected++;
                continue;
            }
            if (hash.equals(subject.getSysacadHash()) && subject.isActive()) {
                continue;
            }
            subject.activate();
            subject.setName(command.name());
            subject.setTerm(command.term());
            subject.setSyncedAt(syncedAt);
            subject.setSysacadHash(hash);
            subjectRepository.save(subject);
            affected++;
        }

        return affected;
    }

    // Groups valid commands by (specialty, plan, subject); resolve(specialty, plan) is injective on the
    // study plan, so this key is equivalent to the matching key (subject code, plan id) without
    // creating plans for groups that end up ignored.
    private static Map<SourceKey, List<SubjectSyncCommand>> groupBySourceKey(List<SubjectSyncCommand> commands) {
        Map<SourceKey, List<SubjectSyncCommand>> groups = new LinkedHashMap<>();
        for (SubjectSyncCommand command : commands) {
            if (command.specialtyCode() == null || command.studyPlanCode() == null
                    || command.subjectCode() == null || command.name() == null) {
                log.warn("Materia de SysAcad ignorada por datos incompletos: especialidad={}, plan={}, materia={}",
                        command.specialtyCode(), command.studyPlanCode(), command.subjectCode());
                continue;
            }
            groups.computeIfAbsent(
                            new SourceKey(command.specialtyCode(), command.studyPlanCode(), command.subjectCode()),
                            k -> new ArrayList<>())
                    .add(command);
        }
        return groups;
    }

    private static boolean hasConflictingVariants(List<SubjectSyncCommand> group) {
        return group.stream().map(c -> Hashes.sha256Hex(c.name(), c.term())).distinct().count() > 1;
    }

    private record SourceKey(Integer specialtyCode, Integer studyPlanCode, Integer subjectCode) {
    }

    private record StudyPlanKey(Integer specialtyCode, Integer planCode) {
    }
}
