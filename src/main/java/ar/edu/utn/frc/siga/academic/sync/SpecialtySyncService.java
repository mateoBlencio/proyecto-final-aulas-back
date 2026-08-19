package ar.edu.utn.frc.siga.academic.sync;

import ar.edu.utn.frc.siga.academic.model.Specialty;
import ar.edu.utn.frc.siga.academic.repository.SpecialtyRepository;
import ar.edu.utn.frc.siga.common.model.RecordSource;
import ar.edu.utn.frc.siga.common.util.Hashes;
import ar.edu.utn.frc.siga.sysacad.api.SysacadCatalogReader;
import ar.edu.utn.frc.siga.sysacad.api.SysacadSpecialtyDto;
import ar.edu.utn.frc.siga.sysacad.api.SysacadSyncStateService;
import ar.edu.utn.frc.siga.sysacad.api.SysacadView;
import ar.edu.utn.frc.siga.sysacad.api.SysacadViewSyncer;
import java.time.Instant;
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
public class SpecialtySyncService implements SysacadViewSyncer {

    private final SysacadCatalogReader catalogReader;
    private final SpecialtyRepository specialtyRepository;
    private final SysacadSyncStateService syncStateService;

    @Override
    public SysacadView view() {
        return SysacadView.ESPECIALIDADES;
    }

    @Override
    @Transactional
    public void sync() {
        try {
            int affected = upsert(catalogReader.findSpecialties());
            syncStateService.recordSuccess(SysacadView.ESPECIALIDADES, affected);
            log.info("Sync de Especialidades finalizado: {} filas afectadas", affected);
        } catch (RuntimeException e) {
            syncStateService.recordFailure(SysacadView.ESPECIALIDADES, e.getMessage());
            throw e;
        }
    }

    private int upsert(List<SysacadSpecialtyDto> rows) {
        Instant syncedAt = Instant.now();
        Map<Integer, Specialty> existing = specialtyRepository.findAll().stream()
                .collect(Collectors.toMap(Specialty::getSpecialtyCode, Function.identity()));
        Set<Integer> incoming = new HashSet<>();
        int affected = 0;

        for (SysacadSpecialtyDto row : rows) {
            if (row.specialtyCode() == null) {
                log.warn("Especialidad de SysAcad ignorada por clave vacía: nombre={}", row.name());
                continue;
            }
            incoming.add(row.specialtyCode());
            String hash = Hashes.sha256Hex(row.name());
            Specialty specialty = existing.get(row.specialtyCode());

            if (specialty == null) {
                Specialty saved = specialtyRepository.save(Specialty.builder()
                        .specialtyCode(row.specialtyCode())
                        .name(row.name())
                        .source(RecordSource.SYSACAD)
                        .syncedAt(syncedAt)
                        .sysacadHash(hash)
                        .presentInSysacad(true)
                        .build());
                existing.put(row.specialtyCode(), saved);
                affected++;
                continue;
            }
            if (isUpToDate(specialty, hash)) {
                continue;
            }
            specialty.setName(row.name());
            specialty.setSource(RecordSource.SYSACAD);
            specialty.setSyncedAt(syncedAt);
            specialty.setSysacadHash(hash);
            specialty.setPresentInSysacad(true);
            specialtyRepository.save(specialty);
            affected++;
        }

        return affected + markAbsent(existing.values(), incoming, syncedAt);
    }

    // `especialidad` no tiene flag de vigencia y el soft-delete la ocultaría a planes y materias
    // que la referencian, así que la baja upstream solo se refleja en `vigente_sysacad` (§6.2).
    private int markAbsent(Iterable<Specialty> existing, Set<Integer> incoming, Instant syncedAt) {
        int affected = 0;
        for (Specialty specialty : existing) {
            if (incoming.contains(specialty.getSpecialtyCode())
                    || specialty.getSource() != RecordSource.SYSACAD
                    || Boolean.FALSE.equals(specialty.getPresentInSysacad())) {
                continue;
            }
            specialty.setPresentInSysacad(false);
            specialty.setSyncedAt(syncedAt);
            specialtyRepository.save(specialty);
            affected++;
            log.info("Especialidad marcada como no vigente en SysAcad: codigo={}", specialty.getSpecialtyCode());
        }
        return affected;
    }

    private static boolean isUpToDate(Specialty specialty, String hash) {
        return hash.equals(specialty.getSysacadHash())
                && Boolean.TRUE.equals(specialty.getPresentInSysacad())
                && specialty.getSource() == RecordSource.SYSACAD;
    }
}
