package ar.edu.utn.frc.siga.space.service.impl;

import ar.edu.utn.frc.siga.common.dto.FindOrCreateResult;
import ar.edu.utn.frc.siga.common.exception.ResourceNotFoundException;
import ar.edu.utn.frc.siga.space.dto.response.BuildingResponseDto;
import ar.edu.utn.frc.siga.space.model.Building;
import ar.edu.utn.frc.siga.space.repository.BuildingRepository;
import ar.edu.utn.frc.siga.space.service.BuildingService;
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

    @Override
    public Building findById(Integer id) {
        log.debug("Fetching active building: id={}", id);
        Building building = findExistingById(id);
        if (!building.getActive()) {
            log.warn("Building lookup rejected: id={} is inactive", id);
            throw ResourceNotFoundException.of("Building", id);
        }
        return building;
    }

    @Override
    public List<BuildingResponseDto> findAll() {
        log.debug("Listing all active buildings");
        return buildingRepository.findAllByDeletedFalse().stream()
                .filter(Building::getActive)
                .map(b -> BuildingResponseDto.builder()
                        .id(b.getId())
                        .name(b.getName())
                        .floorCount(b.getFloorCount())
                        .active(b.getActive())
                        .build())
                .toList();
    }

    @Override
    @Transactional
    public FindOrCreateResult<Building> findOrCreate(String name) {
        return buildingRepository.findByNameAndDeletedFalse(name)
                .map(found -> new FindOrCreateResult<>(found, false))
                .orElseGet(() -> {
                    log.warn("Creando Building con datos provisionales: name={}", name);
                    Building created = buildingRepository.save(
                        Building.builder()
                            .name(name)
                            .floorCount(0)
                            .build()
                    );
                    return new FindOrCreateResult<>(created, true);
                });
    }

    protected Building findExistingById(Integer id) {
        return buildingRepository.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> {
                    log.warn("Building not found: id={}", id);
                    return ResourceNotFoundException.of("Building", id);
                });
    }
}
