package ar.edu.utn.frc.siga.academic.sync;

import ar.edu.utn.frc.siga.academic.model.AcademicPeriod;
import ar.edu.utn.frc.siga.academic.model.Commission;
import ar.edu.utn.frc.siga.academic.model.TermType;
import ar.edu.utn.frc.siga.academic.repository.AcademicPeriodRepository;
import ar.edu.utn.frc.siga.academic.repository.CommissionRepository;
import ar.edu.utn.frc.siga.common.model.RecordSource;
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
        Set<CommissionKey> incoming = new HashSet<>();
        int affected = 0;

        for (SysacadCommissionDto row : rows) {
            if (row.courseCode() == null || row.commissionNumber() == null || row.academicYear() == null) {
                log.warn("Comisión de SysAcad ignorada por clave vacía: curso={}, comision={}, anio={}",
                        row.courseCode(), row.commissionNumber(), row.academicYear());
                continue;
            }
            AcademicPeriod period = findOrCreatePeriod(periodsByYear, row.academicYear());
            CommissionKey rowKey = new CommissionKey(row.courseCode(), row.commissionNumber(), period.getId());
            incoming.add(rowKey);
            String hash = Hashes.sha256Hex(
                    row.courseCode(), row.commissionNumber(), period.getId(),
                    row.specialtyCode(), row.studyPlanCode(), row.subjectCode());
            Commission commission = existing.get(rowKey);

            if (commission == null) {
                Commission saved = commissionRepository.save(Commission.builder()
                        .courseCode(row.courseCode())
                        .commissionNumber(row.commissionNumber())
                        .academicPeriod(period)
                        .source(RecordSource.SYSACAD)
                        .syncedAt(syncedAt)
                        .sysacadHash(hash)
                        .presentInSysacad(true)
                        .build());
                existing.put(rowKey, saved);
                affected++;
                continue;
            }
            if (isUpToDate(commission, hash)) {
                continue;
            }
            commission.setSource(RecordSource.SYSACAD);
            commission.setSyncedAt(syncedAt);
            commission.setSysacadHash(hash);
            commission.setPresentInSysacad(true);
            commissionRepository.save(commission);
            affected++;
        }

        return affected + markAbsent(existing.values(), incoming, syncedAt);
    }

    private AcademicPeriod findOrCreatePeriod(Map<Integer, AcademicPeriod> cache, Integer year) {
        return cache.computeIfAbsent(year, y -> academicPeriodRepository.findByYearAndSemester(y, TermType.ANUAL.getSemester())
                .orElseGet(() -> {
                    log.info("Creando AcademicPeriod para sync de Comisiones: year={}", y);
                    return academicPeriodRepository.save(AcademicPeriod.builder()
                            .year(y)
                            .semester(TermType.ANUAL.getSemester())
                            .startDate(TermType.ANUAL.startDate(y))
                            .endDate(TermType.ANUAL.endDate(y))
                            .build());
                }));
    }

    private int markAbsent(Iterable<Commission> existing, Set<CommissionKey> incoming, Instant syncedAt) {
        int affected = 0;
        for (Commission commission : existing) {
            if (incoming.contains(CommissionKey.of(commission))
                    || commission.getSource() != RecordSource.SYSACAD
                    || Boolean.FALSE.equals(commission.getPresentInSysacad())) {
                continue;
            }
            commission.setPresentInSysacad(false);
            commission.setSyncedAt(syncedAt);
            commissionRepository.save(commission);
            affected++;
            log.info("Comisión marcada como no vigente en SysAcad: curso={}, comision={}",
                    commission.getCourseCode(), commission.getCommissionNumber());
        }
        return affected;
    }

    private static boolean isUpToDate(Commission commission, String hash) {
        return hash.equals(commission.getSysacadHash())
                && Boolean.TRUE.equals(commission.getPresentInSysacad())
                && commission.getSource() == RecordSource.SYSACAD;
    }

    private record CommissionKey(String courseCode, Integer commissionNumber, Long academicPeriodId) {
        static CommissionKey of(Commission commission) {
            return new CommissionKey(commission.getCourseCode(), commission.getCommissionNumber(),
                    commission.getAcademicPeriod().getId());
        }
    }
}
