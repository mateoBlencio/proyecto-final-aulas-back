package ar.edu.utn.frc.siga.academic.service.impl;

import ar.edu.utn.frc.siga.academic.dto.SpecialtyFilter;
import ar.edu.utn.frc.siga.academic.dto.response.SpecialtyResponseDto;
import ar.edu.utn.frc.siga.academic.mapper.SpecialtyMapper;
import ar.edu.utn.frc.siga.academic.model.Specialty;
import ar.edu.utn.frc.siga.academic.repository.SpecialtyRepository;
import ar.edu.utn.frc.siga.academic.repository.SubjectCommissionRepository;
import ar.edu.utn.frc.siga.academic.repository.SubjectRepository;
import ar.edu.utn.frc.siga.academic.service.SpecialtyService;
import ar.edu.utn.frc.siga.academic.service.command.SpecialtySyncCommand;
import ar.edu.utn.frc.siga.academic.specification.SpecialtySpecification;
import ar.edu.utn.frc.siga.common.exception.ResourceNotFoundException;
import ar.edu.utn.frc.siga.common.repository.SoftDeleteSpecifications;
import ar.edu.utn.frc.siga.common.util.Finder;
import ar.edu.utn.frc.siga.common.util.Hashes;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class SpecialtyServiceImpl implements SpecialtyService {

    private final SpecialtyRepository specialtyRepository;
    private final SubjectCommissionRepository subjectCommissionRepository;
    private final SubjectRepository subjectRepository;
    private final SpecialtyMapper specialtyMapper;

    @Override
    public Page<SpecialtyResponseDto> findAll(SpecialtyFilter filter, Pageable pageable, boolean includeDeactivated) {
        Page<Specialty> page = specialtyRepository.findAll(
                SpecialtySpecification.withFilter(filter)
                        .and(SoftDeleteSpecifications.activeUnless(includeDeactivated)),
                pageable);
        Map<Long, Integer> commissions = commissionCounts(page.getContent(), filter, includeDeactivated);
        Map<Long, Integer> subjects = subjectCounts(page.getContent(), filter, includeDeactivated);
        return page.map(specialty -> SpecialtyResponseDto.withCounts(specialtyMapper.toDto(specialty),
                commissions.getOrDefault(specialty.getId(), 0), subjects.getOrDefault(specialty.getId(), 0)));
    }

    // One grouped query per count for the whole page, so the list never costs one count per specialty.
    private Map<Long, Integer> subjectCounts(List<Specialty> specialties, SpecialtyFilter filter,
            boolean includeDeactivated) {
        if (specialties.isEmpty()) {
            return Map.of();
        }
        String pattern = filter.subjectName() == null ? "%" : "%" + filter.subjectName().toLowerCase() + "%";
        return subjectRepository.countSubjectsBySpecialtyIds(
                        specialties.stream().map(Specialty::getId).toList(), filter.subjectCode(), pattern,
                        filter.studyPlanId(), includeDeactivated).stream()
                .collect(Collectors.toMap(row -> (Long) row[0], row -> ((Long) row[1]).intValue()));
    }

    private Map<Long, Integer> commissionCounts(List<Specialty> specialties, SpecialtyFilter filter,
            boolean includeDeactivated) {
        if (specialties.isEmpty()) {
            return Map.of();
        }
        String pattern = filter.commissionCourseCode() == null ? "%"
                : "%" + filter.commissionCourseCode().toLowerCase() + "%";
        return subjectCommissionRepository.countCommissionsBySpecialtyIds(
                        specialties.stream().map(Specialty::getId).toList(), pattern,
                        filter.commissionAcademicPeriodId(), includeDeactivated).stream()
                .collect(Collectors.toMap(row -> (Long) row[0], row -> ((Long) row[1]).intValue()));
    }

    @Override
    @Transactional
    public void activate(Long id) {
        specialtyRepository.restore(Finder.orThrow(specialtyRepository::findById, id, "Specialty"));
    }

    @Override
    @Transactional
    public void deactivate(Long id) {
        specialtyRepository.softDelete(Finder.orThrow(specialtyRepository::findById, id, "Specialty"));
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

    // El sync es un upsert simple por código y nunca reactiva: la baja manual es de la facultad.
    @Override
    @Transactional
    public int syncSpecialties(List<SpecialtySyncCommand> commands) {
        Instant syncedAt = Instant.now();
        Map<Integer, Specialty> existing = specialtyRepository.findAll().stream()
                .collect(Collectors.toMap(Specialty::getSpecialtyCode, Function.identity()));
        Set<Integer> seen = new HashSet<>();
        int affected = 0;

        for (SpecialtySyncCommand command : commands) {
            if (command.specialtyCode() == null) {
                log.warn("Especialidad de SysAcad ignorada por clave vacía: nombre={}", command.name());
                continue;
            }
            if (!seen.add(command.specialtyCode())) {
                log.warn("Especialidad repetida en la misma corrida de SysAcad: especialidad={}",
                        command.specialtyCode());
            }
            String hash = Hashes.sha256Hex(command.name(), command.abbreviation());
            Specialty specialty = existing.get(command.specialtyCode());

            if (specialty == null) {
                existing.put(command.specialtyCode(), specialtyRepository.save(Specialty.builder()
                        .specialtyCode(command.specialtyCode())
                        .name(command.name())
                        .abbreviation(command.abbreviation())
                        .syncedAt(syncedAt)
                        .sysacadHash(hash)
                        .build()));
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
