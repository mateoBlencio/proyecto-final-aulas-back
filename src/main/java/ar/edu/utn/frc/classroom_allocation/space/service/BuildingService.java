package ar.edu.utn.frc.classroom_allocation.space.service;

import ar.edu.utn.frc.classroom_allocation.common.dto.FindOrCreateResult;
import ar.edu.utn.frc.classroom_allocation.space.dto.response.BuildingResponseDto;
import ar.edu.utn.frc.classroom_allocation.space.model.Building;

import java.util.List;

public interface BuildingService {

    Building findById(Integer id);

    Building findByName(String name);

    List<BuildingResponseDto> findAll();

    FindOrCreateResult<Building> findOrCreate(String name);
}
