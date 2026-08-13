package ar.edu.utn.frc.siga.academic.service;

import ar.edu.utn.frc.siga.academic.dto.response.SpecialtyResponseDto;

import java.util.List;

import org.springframework.modulith.NamedInterface;

@NamedInterface("api")
public interface SpecialtyService {

    List<SpecialtyResponseDto> findAll();

    SpecialtyResponseDto findById(Long id);

    SpecialtyResponseDto findBySpecialtyCode(Integer specialtyCode);
}
