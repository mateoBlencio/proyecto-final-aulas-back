package ar.edu.utn.frc.siga.academic.service.impl;

import ar.edu.utn.frc.siga.common.dto.FindOrCreateResult;
import ar.edu.utn.frc.siga.academic.dto.response.CommissionResponseDto;
import ar.edu.utn.frc.siga.academic.mapper.CommissionMapper;
import ar.edu.utn.frc.siga.academic.model.AcademicPeriod;
import ar.edu.utn.frc.siga.academic.model.Commission;
import ar.edu.utn.frc.siga.academic.repository.CommissionRepository;
import ar.edu.utn.frc.siga.academic.service.CommissionService;
import ar.edu.utn.frc.siga.common.exception.ResourceNotFoundException;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class CommissionServiceImpl implements CommissionService {

    private final CommissionRepository commissionRepository;
    private final CommissionMapper commissionMapper;

    @Override
    public Optional<Commission> findById(Long id) {
        log.debug("Finding commission by id={}", id);
        return commissionRepository.findById(id);
    }

    @Override
    @Transactional
    public Commission save(Commission commission) {
        log.debug("Saving commission: courseCode={}, commissionNumber={}",
                commission.getCourseCode(), commission.getCommissionNumber());
        Commission saved = commissionRepository.save(commission);
        log.info("Commission saved: id={}", saved.getId());
        return saved;
    }

    @Override
    @Transactional
    public FindOrCreateResult<Commission> findOrCreate(String courseCode, Integer commissionNumber,
            Integer yearLevel, AcademicPeriod period) {
        return commissionRepository.findByCourseCodeAndCommissionNumberAndAcademicPeriodAndDeletedFalse(
                courseCode, commissionNumber, period)
            .map(found -> new FindOrCreateResult<>(found, false))
            .orElseGet(() -> {
                log.info("Creating Commission: course={}, commission={}, period={}",
                    courseCode, commissionNumber, period.getId());
                Commission created = commissionRepository.save(
                    Commission.builder()
                        .courseCode(courseCode)
                        .commissionNumber(commissionNumber)
                        .yearLevel(yearLevel)
                        .academicPeriod(period)
                        .build()
                );
                return new FindOrCreateResult<>(created, true);
            });
    }

    @Override
    public List<CommissionResponseDto> findDtosByIds(Collection<Long> ids) {
        return commissionRepository.findAllById(ids).stream()
                .map(commissionMapper::toDto)
                .toList();
    }

    @Override
    public CommissionResponseDto findDtoById(Long id) {
        return commissionMapper.toDto(commissionRepository.findById(id)
                .orElseThrow(() -> ResourceNotFoundException.of("Commission", id)));
    }
}
