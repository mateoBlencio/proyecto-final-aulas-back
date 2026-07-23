package ar.edu.utn.frc.siga.academic.service;

import ar.edu.utn.frc.siga.academic.dto.response.SpecialtyResponseDto;

import java.util.List;

import org.springframework.modulith.NamedInterface;

/** Fachada de especialidades: son datos de catálogo, cargados por fuera de esta app (no crea). */
@NamedInterface("api")
public interface SpecialtyService {

    List<SpecialtyResponseDto> findAll();

    SpecialtyResponseDto findById(Long id);

    /** Busca por código de especialidad; lanza {@code ResourceNotFoundException} si no existe. */
    SpecialtyResponseDto findBySpecialtyCode(Integer specialtyCode);
}
