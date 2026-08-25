package ar.edu.utn.frc.siga.space.service.impl;

import ar.edu.utn.frc.siga.common.exception.ResourceNotFoundException;
import ar.edu.utn.frc.siga.space.config.SpaceSettings;
import ar.edu.utn.frc.siga.space.dto.request.BuildingActiveBatchItemDto;
import ar.edu.utn.frc.siga.space.dto.response.BuildingResponseDto;
import ar.edu.utn.frc.siga.space.mapper.BuildingMapper;
import ar.edu.utn.frc.siga.space.model.Building;
import ar.edu.utn.frc.siga.space.repository.BuildingRepository;
import ar.edu.utn.frc.siga.space.service.BuildingService;
import java.time.Instant;
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
}
