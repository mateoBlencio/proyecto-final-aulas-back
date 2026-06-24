package ar.edu.utn.frc.classroom_allocation.space.service.impl;

import ar.edu.utn.frc.classroom_allocation.common.dto.FindOrCreateResult;
import ar.edu.utn.frc.classroom_allocation.common.exception.ResourceNotFoundException;
import ar.edu.utn.frc.classroom_allocation.space.model.Building;
import ar.edu.utn.frc.classroom_allocation.space.repository.BuildingRepository;
import ar.edu.utn.frc.classroom_allocation.space.service.BuildingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;


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
            throw new ResourceNotFoundException("Building not found with id: " + id);
        }
        return building;
    }

    @Override
    public Building findByName(String buildingName) {
        return buildingRepository.findByNameAndDeletedFalse(buildingName)
                .orElseThrow(() -> {
                    log.warn("Building not found: name={}", buildingName);
                    return new ResourceNotFoundException("Building not found with name: " + buildingName);
                });
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
                    return new ResourceNotFoundException("Building not found with id: " + id);
                });
    }
}
