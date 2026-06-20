package ar.edu.utn.frc.classroom_allocation.career.service.impl;

import ar.edu.utn.frc.classroom_allocation.career.model.Specialty;
import ar.edu.utn.frc.classroom_allocation.career.repository.SpecialtyRepository;
import ar.edu.utn.frc.classroom_allocation.career.service.SpecialtyService;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class SpecialtyServiceImpl implements SpecialtyService {

    private final SpecialtyRepository specialtyRepository;

    @Override
    public Optional<Specialty> findBySpecialtyCodeAndDeletedFalse(Integer specialtyCode) {
        return specialtyRepository.findBySpecialtyCodeAndDeletedFalse(specialtyCode);
    }

    @Override
    @Transactional
    public Specialty save(Specialty specialty) {
        return specialtyRepository.save(specialty);
    }
}
