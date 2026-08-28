package ar.edu.utn.frc.siga.academic.service.impl;

import ar.edu.utn.frc.siga.academic.dto.response.SpecialtyResponseDto;
import ar.edu.utn.frc.siga.academic.mapper.SpecialtyMapper;
import ar.edu.utn.frc.siga.academic.model.Specialty;
import ar.edu.utn.frc.siga.academic.repository.SpecialtyRepository;
import ar.edu.utn.frc.siga.academic.service.SpecialtyService;
import ar.edu.utn.frc.siga.academic.service.command.SpecialtySyncCommand;
import ar.edu.utn.frc.siga.common.exception.ResourceNotFoundException;
import ar.edu.utn.frc.siga.common.util.Hashes;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
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

    // `especialidad` no tiene flag de vigencia: SysAcad es la única fuente y no hay
    // registro local a preservar, así que el sync es un upsert simple por código.
    @Override
    @Transactional
    public int syncSpecialties(List<SpecialtySyncCommand> commands) {
        Instant syncedAt = Instant.now();
        Map<Integer, Specialty> existing = specialtyRepository.findAll().stream()
                .collect(Collectors.toMap(Specialty::getSpecialtyCode, Function.identity()));
        int affected = 0;

        for (SpecialtySyncCommand command : commands) {
            if (command.specialtyCode() == null) {
                log.warn("Especialidad de SysAcad ignorada por clave vacía: nombre={}", command.name());
                continue;
            }
            String hash = Hashes.sha256Hex(command.name(), command.abbreviation());
            Specialty specialty = existing.get(command.specialtyCode());

            if (specialty == null) {
                specialtyRepository.save(Specialty.builder()
                        .specialtyCode(command.specialtyCode())
                        .name(command.name())
                        .abbreviation(command.abbreviation())
                        .syncedAt(syncedAt)
                        .sysacadHash(hash)
                        .build());
                affected++;
                continue;
            }
            if (hash.equals(specialty.getSysacadHash())) {
                continue;
            }
            specialty.setName(command.name());
            specialty.setAbbreviation(command.abbreviation());
            specialty.setSyncedAt(syncedAt);
            specialty.setSysacadHash(hash);
            specialtyRepository.save(specialty);
            affected++;
        }

        return affected;
    }
}
