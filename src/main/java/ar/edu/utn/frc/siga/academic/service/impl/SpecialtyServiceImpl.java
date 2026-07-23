package ar.edu.utn.frc.siga.academic.service.impl;

import ar.edu.utn.frc.siga.academic.dto.response.SpecialtyResponseDto;
import ar.edu.utn.frc.siga.academic.mapper.SpecialtyMapper;
import ar.edu.utn.frc.siga.academic.repository.SpecialtyRepository;
import ar.edu.utn.frc.siga.academic.service.SpecialtyService;
import ar.edu.utn.frc.siga.common.exception.ResourceNotFoundException;
import java.util.List;
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
    public List<SpecialtyResponseDto> findAll() {
        return specialtyRepository.findAll().stream()
                .map(specialtyMapper::toDto)
                .toList();
    }

    @Override
    public SpecialtyResponseDto findById(Long id) {
        return specialtyMapper.toDto(specialtyRepository.findById(id)
                .orElseThrow(() -> ResourceNotFoundException.of("Specialty", id)));
    }

    @Override
    public SpecialtyResponseDto findBySpecialtyCode(Integer specialtyCode) {
        return specialtyMapper.toDto(specialtyRepository.findBySpecialtyCode(specialtyCode)
                .orElseThrow(() -> ResourceNotFoundException.of("Specialty", specialtyCode)));
    }
}
