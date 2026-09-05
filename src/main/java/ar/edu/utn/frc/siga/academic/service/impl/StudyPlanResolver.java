package ar.edu.utn.frc.siga.academic.service.impl;

import ar.edu.utn.frc.siga.academic.model.Specialty;
import ar.edu.utn.frc.siga.academic.model.StudyPlan;
import ar.edu.utn.frc.siga.academic.repository.SpecialtyRepository;
import ar.edu.utn.frc.siga.academic.repository.StudyPlanRepository;
import ar.edu.utn.frc.siga.common.dto.FindOrCreateResult;
import ar.edu.utn.frc.siga.common.util.Hashes;
import java.time.Instant;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
class StudyPlanResolver {

    private final SpecialtyRepository specialtyRepository;
    private final StudyPlanRepository studyPlanRepository;

    Optional<StudyPlan> findOrCreate(Integer specialtyCode, Integer planCode, Instant syncedAt) {
        if (specialtyCode == null || planCode == null) {
            return Optional.empty();
        }
        Specialty specialty = specialtyRepository.findBySpecialtyCode(specialtyCode)
                .orElseGet(() -> {
                    log.info("Creando Specialty stub: especialidad={}", specialtyCode);
                    return specialtyRepository.save(Specialty.builder()
                            .specialtyCode(specialtyCode)
                            .name("Especialidad " + specialtyCode)
                            .syncedAt(syncedAt)
                            .sysacadHash(null)
                            .build());
                });
        return Optional.of(FindOrCreateResult.resolve(
                studyPlanRepository.findByPlanCodeAndSpecialty(planCode, specialty),
                () -> {
                    log.info("Creando StudyPlan: especialidad={}, plan={}", specialtyCode, planCode);
                    return studyPlanRepository.save(StudyPlan.builder()
                            .planCode(planCode)
                            .specialty(specialty)
                            .syncedAt(syncedAt)
                            .sysacadHash(Hashes.sha256Hex(specialtyCode, planCode))
                            .build());
                }).value());
    }
}
