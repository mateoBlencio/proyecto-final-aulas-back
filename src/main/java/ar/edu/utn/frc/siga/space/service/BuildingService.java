package ar.edu.utn.frc.siga.space.service;

import ar.edu.utn.frc.siga.common.dto.FindOrCreateResult;
import ar.edu.utn.frc.siga.space.dto.response.BuildingResponseDto;

import java.util.List;

import org.springframework.modulith.NamedInterface;

/**
 * Fachada pública del módulo {@code space} para consulta y resolución de edificios.
 */
@NamedInterface("api")
public interface BuildingService {

    /** Edificios activos ({@code active = true}), para poblar filtros de asignación. */
    List<BuildingResponseDto> findAll();

    /**
     * Busca un edificio por nombre exacto; si no existe, lo crea con datos provisionales
     * (piso 0). Usado por flujos que reciben el nombre del edificio como texto libre
     * (p. ej. importación de Excel) y no pueden fallar por falta de un maestro previo.
     */
    FindOrCreateResult<BuildingResponseDto> findOrCreate(String name);
}
