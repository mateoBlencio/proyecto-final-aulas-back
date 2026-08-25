package ar.edu.utn.frc.siga.space.service;

import ar.edu.utn.frc.siga.space.dto.request.BuildingActiveBatchItemDto;
import ar.edu.utn.frc.siga.space.dto.response.BuildingResponseDto;

import java.util.List;

import org.springframework.modulith.NamedInterface;

@NamedInterface("api")
public interface BuildingService {

    List<BuildingResponseDto> findAll(boolean includeInactive);

    BuildingResponseDto findById(Long id);

    BuildingResponseDto findByName(String name);

    BuildingResponseDto setActive(Long id, Boolean active);

    List<BuildingResponseDto> setActiveBatch(List<BuildingActiveBatchItemDto> items);
}
