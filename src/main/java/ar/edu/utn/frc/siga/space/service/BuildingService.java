package ar.edu.utn.frc.siga.space.service;

import ar.edu.utn.frc.siga.space.dto.request.BuildingActiveBatchItemDto;
import ar.edu.utn.frc.siga.space.dto.response.BuildingResponseDto;
import ar.edu.utn.frc.siga.common.service.ActivationService;
import ar.edu.utn.frc.siga.space.service.command.BuildingSyncCommand;

import java.util.List;

import org.springframework.modulith.NamedInterface;

@NamedInterface("api")
public interface BuildingService extends ActivationService<Long> {

    List<BuildingResponseDto> findAll(boolean includeDeactivated);

    BuildingResponseDto findById(Long id);

    BuildingResponseDto findByName(String name);

    BuildingResponseDto setActive(Long id, Boolean active);

    List<BuildingResponseDto> setActiveBatch(List<BuildingActiveBatchItemDto> items);

    /**
     * Sincroniza el lote de edificios provenientes de SysAcad: crea/actualiza por código comparando
     * hash y marca (soft-delete) los ausentes en la corrida. Devuelve la cantidad de filas afectadas.
     */
    int syncBuildings(List<BuildingSyncCommand> commands);
}
