package ar.edu.utn.frc.siga.academic.sync;

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
import ar.edu.utn.frc.siga.common.dto.FindOrCreateResult;
import ar.edu.utn.frc.siga.common.util.Hashes;
import ar.edu.utn.frc.siga.common.util.Maps;
import ar.edu.utn.frc.siga.sysacad.api.SysacadCatalogReader;
import ar.edu.utn.frc.siga.sysacad.api.SysacadCommissionDto;
import ar.edu.utn.frc.siga.sysacad.api.SysacadSubjectCommissionDto;
import ar.edu.utn.frc.siga.sysacad.api.SysacadSyncStateService;
import ar.edu.utn.frc.siga.sysacad.api.SysacadView;
import ar.edu.utn.frc.siga.sysacad.api.SysacadViewSyncer;
import java.time.Instant;
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
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "siga.sysacad", name = "enabled", havingValue = "true")
public class CommissionSyncService implements SysacadViewSyncer {

    private final SysacadCatalogReader catalogReader;
    private final CommissionRepository commissionRepository;
    private final AcademicPeriodRepository academicPeriodRepository;
    private final SubjectRepository subjectRepository;
    private final SubjectCommissionRepository subjectCommissionRepository;
    private final StudyPlanResolver studyPlanResolver;
    private final SysacadSyncStateService syncStateService;

    @Override
    public SysacadView view() {
        return SysacadView.COMISIONES;
    }

    @Override
    @Transactional
    public void sync() {
        try {
            int affected = upsert(catalogReader.findCommissions());
            syncStateService.recordSuccess(SysacadView.COMISIONES, affected);
            log.info("Sync de Comisiones finalizado: {} filas afectadas", affected);
        } catch (RuntimeException e) {
            syncStateService.recordFailure(SysacadView.COMISIONES, e.getMessage());
            throw e;
        }
    }

    private int upsert(List<SysacadCommissionDto> rows) {
        Instant syncedAt = Instant.now();
        Map<CommissionKey, Commission> existingCommissions = commissionRepository.findAll().stream()
                .collect(Collectors.toMap(CommissionKey::of, Function.identity()));
        Map<Integer, AcademicPeriod> periodsByYear = new HashMap<>();
        Map<StudyPlanKey, Optional<StudyPlan>> studyPlansByKey = new HashMap<>();
        Map<SubjectKey, Subject> existingSubjects = Maps.byId(subjectRepository.findAll(), SubjectKey::of);
        Map<EnrollmentKey, SysacadSubjectCommissionDto> enrollments =
                Maps.byId(catalogReader.findSubjectCommissions(), EnrollmentKey::of);
        Map<SubjectCommissionId, SubjectCommission> existingLinks =
                Maps.byId(subjectCommissionRepository.findAll(), SubjectCommission::getId);
        Set<CommissionKey> incoming = new HashSet<>();
        int affected = 0;

        for (SysacadCommissionDto row : rows) {
            if (row.courseCode() == null || row.academicYear() == null) {
                log.warn("Comisión de SysAcad ignorada por clave vacía: curso={}, anio={}",
                        row.courseCode(), row.academicYear());
                continue;
            }
            Optional<StudyPlan> studyPlan =
                    findOrCreateStudyPlan(studyPlansByKey, row.specialtyCode(), row.studyPlanCode(), syncedAt);
            AcademicPeriod period = findOrCreatePeriod(periodsByYear, row.academicYear());
            CommissionKey rowKey = new CommissionKey(row.courseCode(), period.getId());
            incoming.add(rowKey);
            String hash = Hashes.sha256Hex(
                    row.courseCode(), period.getId(),
                    row.specialtyCode(), row.studyPlanCode(), row.subjectCode());
            Commission commission = existingCommissions.get(rowKey);

            if (commission == null) {
                commission = commissionRepository.save(Commission.builder()
                        .courseCode(row.courseCode())
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

            affected += linkSubjectCommission(existingSubjects, existingLinks, enrollments, studyPlan, commission, row);
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
            Map<EnrollmentKey, SysacadSubjectCommissionDto> enrollments,
            Optional<StudyPlan> studyPlan, Commission commission, SysacadCommissionDto row) {
        if (studyPlan.isEmpty() || row.subjectCode() == null) {
            return 0;
        }
        Subject subject = existingSubjects.get(new SubjectKey(row.subjectCode(), studyPlan.get().getId()));
        if (subject == null) {
            log.warn("No se pudo resolver la materia {} para la comisión {}: no está sincronizada todavía",
                    row.subjectCode(), row.courseCode());
            return 0;
        }
        SysacadSubjectCommissionDto enrollment = enrollments.get(new EnrollmentKey(row.courseCode(), row.subjectCode()));
        if (enrollment == null || enrollment.enrolledCount() == null) {
            log.warn("No se pudo resolver la cantidad de inscriptos: curso={}, materia={}",
                    row.courseCode(), row.subjectCode());
            return 0;
        }
        SubjectCommissionId id = new SubjectCommissionId(subject.getId(), commission.getId());
        SubjectCommission link = existingLinks.get(id);

        if (link == null) {
            subjectCommissionRepository.save(SubjectCommission.builder()
                    .id(new SubjectCommissionId())
                    .subject(subject)
                    .commission(commission)
                    .enrolledCount(enrollment.enrolledCount())
                    .build());
            return 1;
        }
        if (enrollment.enrolledCount().equals(link.getEnrolledCount())) {
            return 0;
        }
        link.setEnrolledCount(enrollment.enrolledCount());
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

    private record EnrollmentKey(String courseCode, Integer subjectCode) {
        static EnrollmentKey of(SysacadSubjectCommissionDto dto) {
            return new EnrollmentKey(dto.courseCode(), dto.subjectCode());
        }
    }
}
