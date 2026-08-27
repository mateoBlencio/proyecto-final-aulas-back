package ar.edu.utn.frc.siga.space.service.impl;

import ar.edu.utn.frc.siga.common.exception.ResourceNotFoundException;
import ar.edu.utn.frc.siga.common.util.Hashes;
import ar.edu.utn.frc.siga.space.config.SpaceSettings;
import ar.edu.utn.frc.siga.space.dto.request.BuildingActiveBatchItemDto;
import ar.edu.utn.frc.siga.space.dto.response.BuildingResponseDto;
import ar.edu.utn.frc.siga.space.mapper.BuildingMapper;
import ar.edu.utn.frc.siga.space.model.Building;
import ar.edu.utn.frc.siga.space.repository.BuildingRepository;
import ar.edu.utn.frc.siga.space.service.BuildingService;
import ar.edu.utn.frc.siga.space.service.command.BuildingSyncCommand;
import java.time.Instant;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
    private final SpaceSettings spaceSettings;

    @Override
    public List<BuildingResponseDto> findAll(boolean includeInactive) {
        boolean filterInactive = !includeInactive && spaceSettings.isFilterInactiveBuildings();
        log.debug("Listando edificios: includeInactive={}, filterInactive={}", includeInactive, filterInactive);
        return buildingRepository.findAll().stream()
                .filter(building -> !filterInactive || building.getDeletedAt() == null)
                .map(buildingMapper::toDto)
                .toList();
    }

    @Override
    public BuildingResponseDto findById(Long id) {
        return buildingMapper.toDto(buildingRepository.findById(id)
                .orElseThrow(() -> ResourceNotFoundException.of("Building", id)));
    }

    @Override
    public BuildingResponseDto findByName(String name) {
        return buildingMapper.toDto(buildingRepository.findByName(name)
                .orElseThrow(() -> ResourceNotFoundException.of("Building", name)));
    }

    @Override
    @Transactional
    public BuildingResponseDto setActive(Long id, Boolean active) {
        log.debug("Cambiando estado activo del edificio: id={}, active={}", id, active);
        Building building = buildingRepository.findById(id)
                .orElseThrow(() -> ResourceNotFoundException.of("Building", id));
        building.setDeletedAt(Boolean.TRUE.equals(active) ? null : Instant.now());
        Building saved = buildingRepository.save(building);
        log.info("Edificio {} {}", id, active ? "activado" : "desactivado");

        return buildingMapper.toDto(saved);
    }

    @Override
    @Transactional
    public List<BuildingResponseDto> setActiveBatch(List<BuildingActiveBatchItemDto> items) {
        log.debug("Actualizando estado activo en lote: count={}", items.size());
        return items.stream()
                .map(item -> setActive(item.id(), item.active()))
                .toList();
    }

    @Override
    @Transactional
    public int syncBuildings(List<BuildingSyncCommand> commands) {
        Instant syncedAt = Instant.now();
        Map<Integer, Building> existing = buildingRepository.findAll().stream()
                .filter(building -> building.getBuildingCode() != null)
                .collect(Collectors.toMap(Building::getBuildingCode, Function.identity()));
        Set<Integer> incoming = new HashSet<>();
        int affected = 0;

        for (BuildingSyncCommand command : commands) {
            if (command.buildingCode() == null || command.name() == null || command.name().isBlank()) {
                log.warn("Edificio de SysAcad ignorado por clave o nombre vacíos: codigo={}", command.buildingCode());
                continue;
            }
            incoming.add(command.buildingCode());
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
            building.setDeletedAt(null);
            building.setName(command.name());
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
