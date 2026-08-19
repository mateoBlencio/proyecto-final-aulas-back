package ar.edu.utn.frc.siga.space.service.impl;

import ar.edu.utn.frc.siga.common.exception.ResourceNotFoundException;
import ar.edu.utn.frc.siga.space.config.SpaceSettings;
import ar.edu.utn.frc.siga.space.dto.response.BuildingResponseDto;
import ar.edu.utn.frc.siga.space.mapper.BuildingMapper;
import ar.edu.utn.frc.siga.space.model.Building;
import ar.edu.utn.frc.siga.space.model.Classroom;
import ar.edu.utn.frc.siga.space.repository.BuildingRepository;
import ar.edu.utn.frc.siga.space.repository.ClassroomRepository;
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
    private final ClassroomRepository classroomRepository;
    private final BuildingMapper buildingMapper;
    private final SpaceSettings spaceSettings;

    @Override
    public List<BuildingResponseDto> findAll(boolean includeInactive) {
        boolean filterInactive = !includeInactive && spaceSettings.isFilterInactiveBuildings();
        log.debug("Listando edificios: includeInactive={}, filterInactive={}", includeInactive, filterInactive);
        return buildingRepository.findAll().stream()
                .filter(building -> !filterInactive || building.getActive())
                .map(buildingMapper::toDto)
                .toList();
    }

    @Override
    public BuildingResponseDto findById(Integer id) {
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
    public BuildingResponseDto setActive(Integer id, Boolean active) {
        log.debug("Cambiando estado activo del edificio: id={}, active={}", id, active);
        Building building = buildingRepository.findById(id)
                .orElseThrow(() -> ResourceNotFoundException.of("Building", id));
        building.setActive(active);
        Building saved = buildingRepository.save(building);

        List<Classroom> classrooms = classroomRepository.findByBuilding(building);
        classrooms.forEach(classroom -> classroom.setAvailable(active));
        classroomRepository.saveAll(classrooms);
        log.info("Edificio {} {}: aulas afectadas={}", id, active ? "activado" : "desactivado", classrooms.size());

        return buildingMapper.toDto(saved);
    }
}
