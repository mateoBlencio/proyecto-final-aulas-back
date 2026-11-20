package ar.edu.utn.frc.siga.space.service.impl;

import ar.edu.utn.frc.siga.audit.AuditOperation;
import ar.edu.utn.frc.siga.audit.AuditOperations;
import ar.edu.utn.frc.siga.common.exception.ResourceNotFoundException;
import ar.edu.utn.frc.siga.common.security.BuildingScope;
import ar.edu.utn.frc.siga.common.security.BuildingScopeResolver;
import ar.edu.utn.frc.siga.common.security.BuildingScopedSpecifications;
import ar.edu.utn.frc.siga.common.security.Permission;
import ar.edu.utn.frc.siga.common.util.Finder;
import ar.edu.utn.frc.siga.common.util.Hashes;
import ar.edu.utn.frc.siga.common.util.SyncGuard;
import ar.edu.utn.frc.siga.space.dto.BuildingFilter;
import ar.edu.utn.frc.siga.space.dto.request.BuildingActiveBatchItemDto;
import ar.edu.utn.frc.siga.space.dto.response.BuildingResponseDto;
import ar.edu.utn.frc.siga.space.dto.response.DeactivationImpactDto;
import ar.edu.utn.frc.siga.space.model.DeactivationTarget;
import ar.edu.utn.frc.siga.space.repository.ClassroomRepository;
import ar.edu.utn.frc.siga.space.mapper.BuildingMapper;
import ar.edu.utn.frc.siga.space.model.Building;
import ar.edu.utn.frc.siga.space.repository.BuildingRepository;
import ar.edu.utn.frc.siga.space.service.BuildingService;
import ar.edu.utn.frc.siga.space.service.command.BuildingSyncCommand;
import ar.edu.utn.frc.siga.space.specification.BuildingSpecification;
import java.time.Instant;
import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;


@Slf4j
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class BuildingServiceImpl implements BuildingService {

    private final BuildingRepository buildingRepository;
    private final BuildingMapper buildingMapper;
    private final BuildingScopeResolver buildingScopeResolver;
    private final ClassroomRepository classroomRepository;
    private final ClassroomDeactivationCoordinator deactivationCoordinator;

    @Override
    public Page<BuildingResponseDto> findAll(BuildingFilter filter, Pageable pageable, boolean includeDeactivated) {
        log.debug("Listando edificios: filter={}, pageable={}, includeDeactivated={}",
                filter, pageable, includeDeactivated);
        BuildingScope scope = buildingScopeResolver.scopeFor(Permission.BUILDING_READ);
        return buildingRepository.findAll(
                        BuildingSpecification.withFilter(filter)
                                .and(includeDeactivated ? Specification.<Building>unrestricted() : BuildingSpecification.available())
                                .and(BuildingScopedSpecifications.withinScope(scope, "id")),
                        pageable)
                .map(buildingMapper::toDto);
    }

    @Override
    public BuildingResponseDto findById(Long id) {
        return buildingMapper.toDto(buildingRepository.findById(id)
                .orElseThrow(() -> ResourceNotFoundException.of("Building", id)));
    }

    @Override
    public BuildingResponseDto findByName(String name) {
        return buildingMapper.toDto(buildingRepository.findByName(name)
                .filter(Building::isAvailable)
                .orElseThrow(() -> ResourceNotFoundException.of("Building", name)));
    }

    @Override
    public List<BuildingResponseDto> findByIds(Collection<Long> ids) {
        return buildingRepository.findAllById(ids).stream().map(buildingMapper::toDto).toList();
    }

    @Override
    @Transactional
    @AuditOperation(value = "Cambio de estado de edificio", subject = "Building")
    public BuildingResponseDto setActive(Long id, Boolean active) {
        log.debug("Cambiando estado activo del edificio: id={}, active={}", id, active);
        AuditOperations.describe(Boolean.TRUE.equals(active) ? "Reactivación de edificio" : "Baja de edificio");
        Building building = buildingRepository.findById(id)
                .orElseThrow(() -> ResourceNotFoundException.of("Building", id));
        if (Boolean.TRUE.equals(active)) {
            building.activate();
        } else {
            building.deactivate();
        }
        Building saved = buildingRepository.save(building);
        if (!Boolean.TRUE.equals(active)) {
            deactivationCoordinator.apply(DeactivationTarget.BUILDING, classroomRepository.findIdsByBuildingId(id));
        }
        log.info("Edificio {} {}", id, active ? "activado" : "desactivado");

        return buildingMapper.toDto(saved);
    }

    @Override
    @Transactional
    @AuditOperation(value = "Reactivación de edificio", subject = "Building")
    public void activate(Long id) {
        buildingRepository.restore(Finder.orThrow(buildingRepository::findById, id, "Building"));
    }

    @Override
    @Transactional
    @AuditOperation(value = "Baja de edificio", subject = "Building")
    public DeactivationImpactDto deactivate(Long id) {
        buildingRepository.softDelete(Finder.orThrow(buildingRepository::findById, id, "Building"));
        return deactivationCoordinator.apply(DeactivationTarget.BUILDING, classroomRepository.findIdsByBuildingId(id));
    }

    @Override
    public DeactivationImpactDto deactivationImpact(Long id) {
        Finder.orThrow(buildingRepository::findById, id, "Building");
        return deactivationCoordinator.preview(DeactivationTarget.BUILDING, classroomRepository.findIdsByBuildingId(id));
    }

    @Override
    @Transactional
    @AuditOperation(value = "Cambio de estado de edificios", subject = "Building")
    public List<BuildingResponseDto> setActiveBatch(List<BuildingActiveBatchItemDto> items) {
        log.debug("Actualizando estado activo en lote: count={}", items.size());
        return items.stream()
                .map(item -> setActive(item.id(), item.active()))
                .toList();
    }

    @Override
    @Transactional
    @AuditOperation(value = "Sincronización de edificios desde SysAcad", subject = "Building")
    public int syncBuildings(List<BuildingSyncCommand> commands, int minValidPercent) {
        Instant syncedAt = Instant.now();
        Map<Integer, Building> existing = buildingRepository.findAll().stream()
                .filter(building -> building.getBuildingCode() != null)
                .collect(Collectors.toMap(Building::getBuildingCode, Function.identity()));
        long currentRows = existing.values().stream()
                .filter(building -> building.getSysacadHash() != null
                        && Boolean.TRUE.equals(building.getSysacadEnabled())).count();
        Set<Integer> incoming = new HashSet<>();
        Set<Integer> listed = new HashSet<>();
        int affected = 0;

        for (BuildingSyncCommand command : commands) {
            if (command.buildingCode() != null) {
                listed.add(command.buildingCode());
            }
            if (command.buildingCode() == null || command.name() == null || command.name().isBlank()) {
                log.warn("Edificio de SysAcad ignorado por clave o nombre vacíos: codigo={}", command.buildingCode());
                continue;
            }
            if (!incoming.add(command.buildingCode())) {
                log.warn("Edificio de SysAcad repetido en la vista: se conserva la primera fila, codigo={}",
                        command.buildingCode());
                continue;
            }
            String hash = Hashes.sha256Hex(command.name());
            Building building = existing.get(command.buildingCode());

            if (building == null) {
                Building saved = buildingRepository.save(Building.builder()
                        .buildingCode(command.buildingCode())
                        .name(command.name())
                        .syncedAt(syncedAt)
                        .sysacadHash(hash)
                        .build());
                existing.put(command.buildingCode(), saved);
                affected++;
                continue;
            }
            if (isUpToDate(building, hash)) {
                continue;
            }
            // La baja es de la persona: el sync solo escribe datos y el flag de vigencia, nunca eliminado_en.
            building.setName(command.name());
            building.setSysacadEnabled(true);
            building.setSyncedAt(syncedAt);
            building.setSysacadHash(hash);
            buildingRepository.save(building);
            affected++;
        }

        SyncGuard.requireEnoughValidRows("edificios", incoming.size(), currentRows, minValidPercent);
        // A row Sysacad listed but with an invalid name is not absent: only codes missing from the view are.
        return affected + markAbsent(existing.values(), listed, syncedAt);
    }

    private int markAbsent(Iterable<Building> existing, Set<Integer> incoming, Instant syncedAt) {
        int affected = 0;
        for (Building building : existing) {
            if (building.getSysacadHash() == null || incoming.contains(building.getBuildingCode())
                    || Boolean.FALSE.equals(building.getSysacadEnabled())) {
                continue;
            }
            building.setSysacadEnabled(false);
            building.setSyncedAt(syncedAt);
            buildingRepository.save(building);
            affected++;
            log.info("Edificio marcado como no vigente en SysAcad: codigo={}", building.getBuildingCode());
        }
        return affected;
    }

    private static boolean isUpToDate(Building building, String hash) {
        return hash.equals(building.getSysacadHash()) && Boolean.TRUE.equals(building.getSysacadEnabled());
    }
}
