package ar.edu.utn.frc.siga.academic.sync;

import ar.edu.utn.frc.siga.academic.model.Specialty;
import ar.edu.utn.frc.siga.academic.repository.SpecialtyRepository;
import ar.edu.utn.frc.siga.common.util.Hashes;
import ar.edu.utn.frc.siga.sysacad.api.SysacadCatalogReader;
import ar.edu.utn.frc.siga.sysacad.api.SysacadSpecialtyDto;
import ar.edu.utn.frc.siga.sysacad.api.SysacadSyncStateService;
import ar.edu.utn.frc.siga.sysacad.api.SysacadView;
import ar.edu.utn.frc.siga.sysacad.api.SysacadViewSyncer;
import java.time.Instant;
import java.util.List;
import java.util.Map;
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

    // `especialidad` no tiene flag de vigencia: SysAcad es la única fuente y no hay
    // registro local a preservar, así que el sync es un upsert simple por código.
    private int upsert(List<SysacadSpecialtyDto> rows) {
        Instant syncedAt = Instant.now();
        Map<Integer, Specialty> existing = specialtyRepository.findAll().stream()
                .collect(Collectors.toMap(Specialty::getSpecialtyCode, Function.identity()));
        int affected = 0;

        for (SysacadSpecialtyDto row : rows) {
            if (row.specialtyCode() == null) {
                log.warn("Especialidad de SysAcad ignorada por clave vacía: nombre={}", row.name());
                continue;
            }
            String hash = Hashes.sha256Hex(row.name(), row.abbreviation());
            Specialty specialty = existing.get(row.specialtyCode());

            if (specialty == null) {
                specialtyRepository.save(Specialty.builder()
                        .specialtyCode(row.specialtyCode())
                        .name(row.name())
                        .abbreviation(row.abbreviation())
                        .syncedAt(syncedAt)
                        .sysacadHash(hash)
                        .build());
                affected++;
                continue;
            }
            if (hash.equals(specialty.getSysacadHash())) {
                continue;
            }
            specialty.setName(row.name());
            specialty.setAbbreviation(row.abbreviation());
            specialty.setSyncedAt(syncedAt);
            specialty.setSysacadHash(hash);
            specialtyRepository.save(specialty);
            affected++;
        }

        return affected;
    }
}
