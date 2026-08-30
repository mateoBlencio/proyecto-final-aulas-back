package ar.edu.utn.frc.siga.academic.service.impl;

import ar.edu.utn.frc.siga.academic.dto.response.CommissionResponseDto;
import ar.edu.utn.frc.siga.academic.mapper.CommissionMapper;
import ar.edu.utn.frc.siga.academic.model.AcademicPeriod;
import ar.edu.utn.frc.siga.academic.model.Commission;
import ar.edu.utn.frc.siga.academic.model.StudyPlan;
import ar.edu.utn.frc.siga.academic.model.Subject;
import ar.edu.utn.frc.siga.academic.model.SubjectCommission;
import ar.edu.utn.frc.siga.academic.model.SubjectCommissionId;
import ar.edu.utn.frc.siga.academic.model.TermType;
import ar.edu.utn.frc.siga.academic.repository.AcademicPeriodRepository;
import ar.edu.utn.frc.siga.academic.repository.CommissionRepository;
import ar.edu.utn.frc.siga.academic.repository.SubjectCommissionRepository;
import ar.edu.utn.frc.siga.academic.repository.SubjectRepository;
import ar.edu.utn.frc.siga.academic.service.CommissionService;
import ar.edu.utn.frc.siga.academic.service.command.CommissionSyncCommand;
import ar.edu.utn.frc.siga.common.dto.FindOrCreateResult;
import ar.edu.utn.frc.siga.common.exception.ResourceNotFoundException;
import ar.edu.utn.frc.siga.common.util.Hashes;
import ar.edu.utn.frc.siga.common.util.Maps;
import java.time.Instant;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class CommissionServiceImpl implements CommissionService {

    private final CommissionRepository commissionRepository;
    private final AcademicPeriodRepository academicPeriodRepository;
    private final SubjectRepository subjectRepository;
    private final SubjectCommissionRepository subjectCommissionRepository;
    private final StudyPlanResolver studyPlanResolver;
    private final CommissionMapper commissionMapper;

    @Override
    public CommissionResponseDto findById(Long id) {
        return commissionMapper.toDto(commissionRepository.findActiveById(id)
                .orElseThrow(() -> ResourceNotFoundException.of("Commission", id)));
    }

    @Override
    public List<CommissionResponseDto> findByIds(Collection<Long> ids) {
        return commissionRepository.findAllById(ids).stream()
                .map(commissionMapper::toDto)
                .toList();
    }

    @Override
    public List<CommissionResponseDto> findAll() {
        return commissionRepository.findAllActive().stream()
                .map(commissionMapper::toDto)
                .toList();
    }

    @Override
    public CommissionResponseDto findByCourseAndPeriod(String courseCode, Integer periodYear,
            Integer periodSemester) {
        AcademicPeriod period = requirePeriod(periodYear, periodSemester);
        return commissionRepository.findByCourseCodeAndAcademicPeriod(courseCode, period)
                .map(commissionMapper::toDto)
                .orElseThrow(() -> ResourceNotFoundException.of("Commission",
                        courseCode + "-" + periodYear + "-" + periodSemester));
    }

    private AcademicPeriod requirePeriod(Integer year, Integer semester) {
        return academicPeriodRepository.findByYearAndSemester(year, semester)
                .orElseThrow(() -> ResourceNotFoundException.of("AcademicPeriod", year + "-" + semester));
    }

    @Override
    public CommissionResponseDto findActiveByCourseCode(String courseCode) {
        List<Commission> active = commissionRepository.findByCourseCodeAndSysacadEnabledTrueAndDeletedAtIsNull(courseCode);
        if (active.size() > 1) {
            log.warn("Más de una comisión vigente en SysAcad para el curso {}: {} candidatas, no se puede "
                    + "resolver sin ambigüedad", courseCode, active.size());
            throw ResourceNotFoundException.of("Commission", courseCode);
        }
        return active.stream().findFirst()
                .map(commissionMapper::toDto)
                .orElseThrow(() -> ResourceNotFoundException.of("Commission", courseCode));
    }

    @Override
    @Transactional
    public int syncCommissions(List<CommissionSyncCommand> commands) {
        Instant syncedAt = Instant.now();
        // Sync/reconciliación: findAll() a propósito (ve filas borradas) para reconciliar por clave natural.
        Map<CommissionKey, Commission> existingCommissions = commissionRepository.findAll().stream()
                .collect(Collectors.toMap(CommissionKey::of, Function.identity()));
        Map<Integer, AcademicPeriod> periodsByYear = new HashMap<>();
        Map<StudyPlanKey, Optional<StudyPlan>> studyPlansByKey = new HashMap<>();
        Map<SubjectKey, Subject> existingSubjects = Maps.byId(subjectRepository.findAll(), SubjectKey::of);
        Map<SubjectCommissionId, SubjectCommission> existingLinks =
                Maps.byId(subjectCommissionRepository.findAll(), SubjectCommission::getId);
        Set<CommissionKey> incoming = new HashSet<>();
        int affected = 0;

        for (CommissionSyncCommand command : commands) {
            if (command.courseCode() == null || command.academicYear() == null) {
                log.warn("Comisión de SysAcad ignorada por clave vacía: curso={}, anio={}",
                        command.courseCode(), command.academicYear());
                continue;
            }
            Optional<StudyPlan> studyPlan =
                    findOrCreateStudyPlan(studyPlansByKey, command.specialtyCode(), command.studyPlanCode(), syncedAt);
            AcademicPeriod period = findOrCreatePeriod(periodsByYear, command.academicYear());
            CommissionKey rowKey = new CommissionKey(command.courseCode(), period.getId());
            incoming.add(rowKey);
            String hash = Hashes.sha256Hex(
                    command.courseCode(), period.getId(),
                    command.specialtyCode(), command.studyPlanCode(), command.subjectCode());
            Commission commission = existingCommissions.get(rowKey);

            if (commission == null) {
                commission = commissionRepository.save(Commission.builder()
                        .courseCode(command.courseCode())
                        .academicPeriod(period)
                        .syncedAt(syncedAt)
                        .sysacadHash(hash)
                        .sysacadEnabled(true)
                        .build());
                existingCommissions.put(rowKey, commission);
                affected++;
            } else if (!isUpToDate(commission, hash)) {
                commission.setSyncedAt(syncedAt);
                commission.setSysacadHash(hash);
                commission.setSysacadEnabled(true);
                commissionRepository.save(commission);
                affected++;
            }

            affected += linkSubjectCommission(existingSubjects, existingLinks, studyPlan, commission, command);
        }

        return affected + markAbsent(existingCommissions.values(), incoming, syncedAt);
    }

    private Optional<StudyPlan> findOrCreateStudyPlan(Map<StudyPlanKey, Optional<StudyPlan>> cache,
            Integer specialtyCode, Integer planCode, Instant syncedAt) {
        if (specialtyCode == null || planCode == null) {
            return Optional.empty();
        }
        StudyPlanKey key = new StudyPlanKey(specialtyCode, planCode);
        Optional<StudyPlan> studyPlan = cache.computeIfAbsent(key,
                k -> studyPlanResolver.findOrCreate(specialtyCode, planCode, syncedAt));
        if (studyPlan.isEmpty()) {
            log.warn("No se pudo resolver el plan de estudio: especialidad {} no sincronizada todavía", specialtyCode);
        }
        return studyPlan;
    }

    private AcademicPeriod findOrCreatePeriod(Map<Integer, AcademicPeriod> cache, Integer year) {
        return cache.computeIfAbsent(year, y -> FindOrCreateResult.resolve(
                academicPeriodRepository.findByYearAndSemester(y, TermType.ANUAL.getSemester()),
                () -> {
                    log.info("Creando AcademicPeriod para sync de Comisiones: year={}", y);
                    return academicPeriodRepository.save(AcademicPeriod.builder()
                            .year(y)
                            .semester(TermType.ANUAL.getSemester())
                            .startDate(TermType.ANUAL.startDate(y))
                            .endDate(TermType.ANUAL.endDate(y))
                            .build());
                }).value());
    }

    private int linkSubjectCommission(Map<SubjectKey, Subject> existingSubjects,
            Map<SubjectCommissionId, SubjectCommission> existingLinks,
            Optional<StudyPlan> studyPlan, Commission commission, CommissionSyncCommand command) {
        if (studyPlan.isEmpty() || command.subjectCode() == null) {
            return 0;
        }
        Subject subject = existingSubjects.get(new SubjectKey(command.subjectCode(), studyPlan.get().getId()));
        if (subject == null) {
            log.warn("No se pudo resolver la materia {} para la comisión {}: no está sincronizada todavía",
                    command.subjectCode(), command.courseCode());
            return 0;
        }
        if (command.enrolledCount() == null) {
            log.warn("No se pudo resolver la cantidad de inscriptos: curso={}, materia={}",
                    command.courseCode(), command.subjectCode());
            return 0;
        }
        SubjectCommissionId id = new SubjectCommissionId(subject.getId(), commission.getId());
        SubjectCommission link = existingLinks.get(id);

        if (link == null) {
            SubjectCommission created = SubjectCommission.builder()
                    .id(new SubjectCommissionId())
                    .subject(subject)
                    .commission(commission)
                    .enrolledCount(command.enrolledCount())
                    .build();
            subjectCommissionRepository.save(created);
            // Registrar el link recién creado para que un duplicado (mismo subject+commission) en el
            // mismo batch entre por la rama de actualización y no vuelva a insertar el mismo id.
            existingLinks.put(id, created);
            return 1;
        }
        if (command.enrolledCount().equals(link.getEnrolledCount())) {
            return 0;
        }
        link.setEnrolledCount(command.enrolledCount());
        subjectCommissionRepository.save(link);
        return 1;
    }

    private int markAbsent(Iterable<Commission> existing, Set<CommissionKey> incoming, Instant syncedAt) {
        int affected = 0;
        for (Commission commission : existing) {
            if (incoming.contains(CommissionKey.of(commission))
                    || Boolean.FALSE.equals(commission.getSysacadEnabled())) {
                continue;
            }
            commission.setSysacadEnabled(false);
            commission.setSyncedAt(syncedAt);
            commissionRepository.save(commission);
            affected++;
            log.info("Comisión marcada como no vigente en SysAcad: curso={}", commission.getCourseCode());
        }
        return affected;
    }

    private static boolean isUpToDate(Commission commission, String hash) {
        return hash.equals(commission.getSysacadHash()) && Boolean.TRUE.equals(commission.getSysacadEnabled());
    }

    private record CommissionKey(String courseCode, Long academicPeriodId) {
        static CommissionKey of(Commission commission) {
            return new CommissionKey(commission.getCourseCode(), commission.getAcademicPeriod().getId());
        }
    }

    private record StudyPlanKey(Integer specialtyCode, Integer planCode) {
    }
}
