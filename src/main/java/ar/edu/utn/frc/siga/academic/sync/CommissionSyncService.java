package ar.edu.utn.frc.siga.academic.sync;

import ar.edu.utn.frc.siga.academic.model.AcademicPeriod;
import ar.edu.utn.frc.siga.academic.model.Commission;
import ar.edu.utn.frc.siga.academic.model.Specialty;
import ar.edu.utn.frc.siga.academic.model.StudyPlan;
import ar.edu.utn.frc.siga.academic.model.TermType;
import ar.edu.utn.frc.siga.academic.repository.AcademicPeriodRepository;
import ar.edu.utn.frc.siga.academic.repository.CommissionRepository;
import ar.edu.utn.frc.siga.academic.repository.SpecialtyRepository;
import ar.edu.utn.frc.siga.academic.repository.StudyPlanRepository;
import ar.edu.utn.frc.siga.common.dto.FindOrCreateResult;
import ar.edu.utn.frc.siga.common.util.Hashes;
import ar.edu.utn.frc.siga.sysacad.api.SysacadCatalogReader;
import ar.edu.utn.frc.siga.sysacad.api.SysacadCommissionDto;
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
    private final SpecialtyRepository specialtyRepository;
    private final StudyPlanRepository studyPlanRepository;
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
        Map<CommissionKey, Commission> existing = commissionRepository.findAll().stream()
                .collect(Collectors.toMap(CommissionKey::of, Function.identity()));
        Map<Integer, AcademicPeriod> periodsByYear = new HashMap<>();
        Map<StudyPlanKey, StudyPlan> studyPlansByKey = new HashMap<>();
        Set<CommissionKey> incoming = new HashSet<>();
        int affected = 0;

        for (SysacadCommissionDto row : rows) {
            if (row.courseCode() == null || row.academicYear() == null) {
                log.warn("Comisión de SysAcad ignorada por clave vacía: curso={}, anio={}",
                        row.courseCode(), row.academicYear());
                continue;
            }
            findOrCreateStudyPlan(studyPlansByKey, row.specialtyCode(), row.studyPlanCode(), syncedAt);
            AcademicPeriod period = findOrCreatePeriod(periodsByYear, row.academicYear());
            CommissionKey rowKey = new CommissionKey(row.courseCode(), period.getId());
            incoming.add(rowKey);
            String hash = Hashes.sha256Hex(
                    row.courseCode(), period.getId(),
                    row.specialtyCode(), row.studyPlanCode(), row.subjectCode());
            Commission commission = existing.get(rowKey);

            if (commission == null) {
                Commission saved = commissionRepository.save(Commission.builder()
                        .courseCode(row.courseCode())
                        .academicPeriod(period)
                        .syncedAt(syncedAt)
                        .sysacadHash(hash)
                        .sysacadEnabled(true)
                        .build());
                existing.put(rowKey, saved);
                affected++;
                continue;
            }
            if (isUpToDate(commission, hash)) {
                continue;
            }
            commission.setSyncedAt(syncedAt);
            commission.setSysacadHash(hash);
            commission.setSysacadEnabled(true);
            commissionRepository.save(commission);
            affected++;
        }

        return affected + markAbsent(existing.values(), incoming, syncedAt);
    }

    private void findOrCreateStudyPlan(Map<StudyPlanKey, StudyPlan> cache, Integer specialtyCode,
            Integer planCode, Instant syncedAt) {
        if (specialtyCode == null || planCode == null) {
            return;
        }
        StudyPlanKey key = new StudyPlanKey(specialtyCode, planCode);
        if (cache.containsKey(key)) {
            return;
        }
        Optional<Specialty> specialty = specialtyRepository.findBySpecialtyCode(specialtyCode);
        if (specialty.isEmpty()) {
            log.warn("No se pudo resolver el plan de estudio: especialidad {} no sincronizada todavía", specialtyCode);
            return;
        }
        StudyPlan studyPlan = FindOrCreateResult.resolve(
                studyPlanRepository.findByPlanCodeAndSpecialty(planCode, specialty.get()),
                () -> {
                    log.info("Creando StudyPlan para sync de Comisiones: especialidad={}, plan={}",
                            specialtyCode, planCode);
                    return studyPlanRepository.save(StudyPlan.builder()
                            .planCode(planCode)
                            .specialty(specialty.get())
                            .syncedAt(syncedAt)
                            .sysacadHash(Hashes.sha256Hex(specialtyCode, planCode))
                            .build());
                }).value();
        cache.put(key, studyPlan);
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
