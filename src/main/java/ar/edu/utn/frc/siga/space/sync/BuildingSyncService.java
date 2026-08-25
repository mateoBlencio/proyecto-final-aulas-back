package ar.edu.utn.frc.siga.space.sync;

import ar.edu.utn.frc.siga.common.util.Hashes;
import ar.edu.utn.frc.siga.space.model.Building;
import ar.edu.utn.frc.siga.space.repository.BuildingRepository;
import ar.edu.utn.frc.siga.sysacad.api.SysacadBuildingDto;
import ar.edu.utn.frc.siga.sysacad.api.SysacadCatalogReader;
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
public class BuildingSyncService implements SysacadViewSyncer {

    private final SysacadCatalogReader catalogReader;
    private final BuildingRepository buildingRepository;
    private final SysacadSyncStateService syncStateService;

    @Override
    public SysacadView view() {
        return SysacadView.EDIFICIOS;
    }

    @Override
    @Transactional
    public void sync() {
        try {
            int affected = upsert(catalogReader.findBuildings());
            syncStateService.recordSuccess(SysacadView.EDIFICIOS, affected);
            log.info("Sync de Edificios finalizado: {} filas afectadas", affected);
        } catch (RuntimeException e) {
            syncStateService.recordFailure(SysacadView.EDIFICIOS, e.getMessage());
            throw e;
        }
    }

    private int upsert(List<SysacadBuildingDto> rows) {
        Instant syncedAt = Instant.now();
        Map<Integer, Building> existing = buildingRepository.findAll().stream()
                .filter(building -> building.getBuildingCode() != null)
                .collect(Collectors.toMap(Building::getBuildingCode, Function.identity()));
        Set<Integer> incoming = new HashSet<>();
        int affected = 0;

        for (SysacadBuildingDto row : rows) {
            if (row.buildingCode() == null || row.name() == null || row.name().isBlank()) {
                log.warn("Edificio de SysAcad ignorado por clave o nombre vacíos: codigo={}", row.buildingCode());
                continue;
            }
            incoming.add(row.buildingCode());
            String hash = Hashes.sha256Hex(row.name());
            Building building = existing.get(row.buildingCode());

            if (building == null) {
                Building saved = buildingRepository.save(Building.builder()
                        .buildingCode(row.buildingCode())
                        .name(row.name())
                        .syncedAt(syncedAt)
                        .sysacadHash(hash)
                        .build());
                existing.put(row.buildingCode(), saved);
                affected++;
                continue;
            }
            if (isUpToDate(building, hash)) {
                continue;
            }
            building.setDeletedAt(null);
            building.setName(row.name());
            building.setSyncedAt(syncedAt);
            building.setSysacadHash(hash);
            buildingRepository.save(building);
            affected++;
        }

        return affected + markAbsent(existing.values(), incoming, syncedAt);
    }

    private int markAbsent(Iterable<Building> existing, Set<Integer> incoming, Instant syncedAt) {
        int affected = 0;
        for (Building building : existing) {
            if (incoming.contains(building.getBuildingCode()) || building.getDeletedAt() != null) {
                continue;
            }
            building.setDeletedAt(syncedAt);
            building.setSyncedAt(syncedAt);
            buildingRepository.save(building);
            affected++;
            log.info("Edificio marcado como no vigente en SysAcad: codigo={}", building.getBuildingCode());
        }
        return affected;
    }

    private static boolean isUpToDate(Building building, String hash) {
        return hash.equals(building.getSysacadHash()) && building.getDeletedAt() == null;
    }
}
