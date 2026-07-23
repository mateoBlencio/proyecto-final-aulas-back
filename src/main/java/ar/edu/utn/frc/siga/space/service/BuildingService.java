package ar.edu.utn.frc.siga.space.service;

import ar.edu.utn.frc.siga.space.dto.response.BuildingResponseDto;

import java.util.List;

import org.springframework.modulith.NamedInterface;

/**
 * Fachada pública del módulo {@code space} para consulta de edificios (datos de
 * catálogo, cargados por fuera de esta app: no crea).
 */
@NamedInterface("api")
public interface BuildingService {

    /** Edificios activos ({@code active = true}), para poblar filtros de asignación. */
    List<BuildingResponseDto> findAll();

    BuildingResponseDto findById(Integer id);

    /** Busca un edificio por nombre exacto; lanza {@code ResourceNotFoundException} si no existe. */
    BuildingResponseDto findByName(String name);
}
