package ar.edu.utn.frc.classroom_allocation.space.service.impl;

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

    protected Building findExistingById(Integer id) {
        return buildingRepository.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> {
                    log.warn("Building not found: id={}", id);
                    return new ResourceNotFoundException("Building not found with id: " + id);
                });
    }
}
