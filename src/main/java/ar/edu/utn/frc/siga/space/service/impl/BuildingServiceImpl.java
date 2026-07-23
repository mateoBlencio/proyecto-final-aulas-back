package ar.edu.utn.frc.siga.space.service.impl;

import ar.edu.utn.frc.siga.common.exception.ResourceNotFoundException;
import ar.edu.utn.frc.siga.space.dto.response.BuildingResponseDto;
import ar.edu.utn.frc.siga.space.mapper.BuildingMapper;
import ar.edu.utn.frc.siga.space.model.Building;
import ar.edu.utn.frc.siga.space.repository.BuildingRepository;
import ar.edu.utn.frc.siga.space.service.BuildingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;


/**
 * Implementación de {@link BuildingService}.
 */
@Slf4j
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class BuildingServiceImpl implements BuildingService {

    private final BuildingRepository buildingRepository;
    private final BuildingMapper buildingMapper;

    @Override
    public List<BuildingResponseDto> findAll() {
        log.debug("Listando todos los edificios activos");
        return buildingRepository.findAll().stream()
                .filter(Building::getActive)
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
}
