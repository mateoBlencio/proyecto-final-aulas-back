package PF.classroom_allocation.space.service.impl;

import PF.classroom_allocation.space.exception.ResourceNotFoundException;
import PF.classroom_allocation.space.model.Building;
import PF.classroom_allocation.space.repository.BuildingRepository;
import PF.classroom_allocation.space.service.BuildingService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class BuildingServiceImpl implements BuildingService {

    private final BuildingRepository buildingRepository;

    @Override
    public Building findById(Integer id) {
        Building building = findExistingById(id);
        if (!building.getActive()) {
            throw new ResourceNotFoundException("Building not found with id: " + id);
        }
        return building;
    }

    @Transactional(readOnly = true)
    protected Building findExistingById(Integer id) {
        return buildingRepository.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> new ResourceNotFoundException("Building not found with id: " + id));
    }

}
