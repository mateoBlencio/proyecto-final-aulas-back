package ar.edu.utn.frc.classroom_allocation.career.service.impl;

import ar.edu.utn.frc.classroom_allocation.career.model.Specialty;
import ar.edu.utn.frc.classroom_allocation.career.repository.SpecialtyRepository;
import ar.edu.utn.frc.classroom_allocation.career.service.SpecialtyService;
import ar.edu.utn.frc.classroom_allocation.common.dto.FindOrCreateResult;
import java.util.Optional;
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

    @Override
    public Optional<Specialty> findBySpecialtyCodeAndDeletedFalse(Integer specialtyCode) {
        log.debug("Finding Specialty: code={}", specialtyCode);
        return specialtyRepository.findBySpecialtyCodeAndDeletedFalse(specialtyCode);
    }

    @Override
    @Transactional
    public Specialty save(Specialty specialty) {
        log.debug("Saving Specialty: code={}", specialty.getSpecialtyCode());
        Specialty saved = specialtyRepository.save(specialty);
        log.info("Specialty saved: id={}", saved.getId());
        return saved;
    }

    @Override
    @Transactional
    public FindOrCreateResult<Specialty> findOrCreate(Integer specialtyCode) {
        return specialtyRepository.findBySpecialtyCodeAndDeletedFalse(specialtyCode)
            .map(found -> new FindOrCreateResult<>(found, false))
            .orElseGet(() -> {
                log.warn("Creando Specialty con nombre provisional: codigo={}", specialtyCode);
                Specialty created = specialtyRepository.save(
                    Specialty.builder()
                        .specialtyCode(specialtyCode)
                        .name(String.valueOf(specialtyCode))
                        .build()
                );
                return new FindOrCreateResult<>(created, true);
            });
    }
}
