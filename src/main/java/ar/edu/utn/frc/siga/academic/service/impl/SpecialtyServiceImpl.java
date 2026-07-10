package ar.edu.utn.frc.siga.academic.service.impl;

import ar.edu.utn.frc.siga.academic.dto.response.SpecialtyResponseDto;
import ar.edu.utn.frc.siga.academic.mapper.SpecialtyMapper;
import ar.edu.utn.frc.siga.academic.model.Specialty;
import ar.edu.utn.frc.siga.academic.repository.SpecialtyRepository;
import ar.edu.utn.frc.siga.academic.service.SpecialtyService;
import ar.edu.utn.frc.siga.common.dto.FindOrCreateResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class SpecialtyServiceImpl implements SpecialtyService {

    private final SpecialtyRepository specialtyRepository;
    private final SpecialtyMapper specialtyMapper;

    @Override
    @Transactional
    public FindOrCreateResult<SpecialtyResponseDto> findOrCreate(Integer specialtyCode) {
        return FindOrCreateResult.resolve(
                specialtyRepository.findBySpecialtyCodeAndDeletedFalse(specialtyCode),
                () -> {
                    log.warn("Creando Specialty con nombre provisional: codigo={}", specialtyCode);
                    return specialtyRepository.save(
                            Specialty.builder()
                                    .specialtyCode(specialtyCode)
                                    .name(String.valueOf(specialtyCode))
                                    .build());
                }
        ).map(specialtyMapper::toDto);
    }
}
