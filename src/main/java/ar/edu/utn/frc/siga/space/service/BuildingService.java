package ar.edu.utn.frc.siga.space.service;

import ar.edu.utn.frc.siga.common.dto.FindOrCreateResult;
import ar.edu.utn.frc.siga.space.dto.response.BuildingResponseDto;
import ar.edu.utn.frc.siga.space.model.Building;

import java.util.List;

import org.springframework.modulith.NamedInterface;

@NamedInterface("api")
public interface BuildingService {

    Building findById(Integer id);

    Building findByName(String name);

    List<BuildingResponseDto> findAll();

    FindOrCreateResult<Building> findOrCreate(String name);
}
