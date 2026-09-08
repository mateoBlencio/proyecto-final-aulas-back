package ar.edu.utn.frc.siga.academic.service;

import ar.edu.utn.frc.siga.academic.dto.SpecialtyFilter;
import ar.edu.utn.frc.siga.academic.dto.response.SpecialtyResponseDto;
import ar.edu.utn.frc.siga.academic.service.command.SpecialtySyncCommand;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.modulith.NamedInterface;

@NamedInterface("api")
public interface SpecialtyService {

    Page<SpecialtyResponseDto> findAll(SpecialtyFilter filter, Pageable pageable);

    SpecialtyResponseDto findById(Long id);

    SpecialtyResponseDto findBySpecialtyCode(Integer specialtyCode);

    /**
     * Sincroniza el lote de especialidades provenientes de SysAcad (upsert simple por código,
     * comparando hash). Devuelve la cantidad de filas afectadas.
     */
    int syncSpecialties(List<SpecialtySyncCommand> commands);
}
