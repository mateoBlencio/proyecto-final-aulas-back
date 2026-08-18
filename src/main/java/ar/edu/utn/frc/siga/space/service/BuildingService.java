package ar.edu.utn.frc.siga.space.service;

import ar.edu.utn.frc.siga.space.dto.response.BuildingResponseDto;

import java.util.List;

import org.springframework.modulith.NamedInterface;

@NamedInterface("api")
public interface BuildingService {

    List<BuildingResponseDto> findAll();

    BuildingResponseDto findById(Integer id);

    BuildingResponseDto findByName(String name);
}
