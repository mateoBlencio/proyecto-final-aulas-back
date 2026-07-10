package ar.edu.utn.frc.siga.space.service.impl;

import ar.edu.utn.frc.siga.common.dto.FindOrCreateResult;
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


@Slf4j
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class BuildingServiceImpl implements BuildingService {

    private final BuildingRepository buildingRepository;
    private final BuildingMapper buildingMapper;

    @Override
    public List<BuildingResponseDto> findAll() {
        log.debug("Listing all active buildings");
        return buildingRepository.findAll().stream()
                .filter(Building::getActive)
                .map(buildingMapper::toDto)
                .toList();
    }

    @Override
    @Transactional
    public FindOrCreateResult<BuildingResponseDto> findOrCreate(String name) {
        return FindOrCreateResult.resolve(
                buildingRepository.findByName(name),
                () -> {
                    log.warn("Creando Building con datos provisionales: name={}", name);
                    return buildingRepository.save(
                            Building.builder()
                                    .name(name)
                                    .floorCount(0)
                                    .build());
                }
        ).map(buildingMapper::toDto);
    }
}
