package ar.edu.utn.frc.siga.academic.service.impl;

import ar.edu.utn.frc.siga.academic.dto.response.CommissionResponseDto;
import ar.edu.utn.frc.siga.academic.mapper.CommissionMapper;
import ar.edu.utn.frc.siga.academic.model.AcademicPeriod;
import ar.edu.utn.frc.siga.academic.repository.AcademicPeriodRepository;
import ar.edu.utn.frc.siga.academic.repository.CommissionRepository;
import ar.edu.utn.frc.siga.academic.service.CommissionService;
import ar.edu.utn.frc.siga.common.exception.ResourceNotFoundException;
import java.util.Collection;
import java.util.List;
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
    private final AcademicPeriodRepository academicPeriodRepository;
    private final CommissionMapper commissionMapper;

    @Override
    public CommissionResponseDto findById(Long id) {
        return commissionMapper.toDto(commissionRepository.findById(id)
                .orElseThrow(() -> ResourceNotFoundException.of("Commission", id)));
    }

    @Override
    public List<CommissionResponseDto> findByIds(Collection<Long> ids) {
        return commissionRepository.findAllById(ids).stream()
                .map(commissionMapper::toDto)
                .toList();
    }

    @Override
    public List<CommissionResponseDto> findAll() {
        return commissionRepository.findAll().stream()
                .map(commissionMapper::toDto)
                .toList();
    }

    @Override
    public CommissionResponseDto findByCourseAndNumberAndPeriod(String courseCode, Integer commissionNumber,
            Integer periodYear, Integer periodSemester) {
        AcademicPeriod period = requirePeriod(periodYear, periodSemester);
        return commissionRepository.findByCourseCodeAndCommissionNumberAndAcademicPeriod(
                        courseCode, commissionNumber, period)
                .map(commissionMapper::toDto)
                .orElseThrow(() -> ResourceNotFoundException.of("Commission",
                        courseCode + "-" + commissionNumber + "-" + periodYear + "-" + periodSemester));
    }

    private AcademicPeriod requirePeriod(Integer year, Integer semester) {
        return academicPeriodRepository.findByYearAndSemester(year, semester)
                .orElseThrow(() -> ResourceNotFoundException.of("AcademicPeriod", year + "-" + semester));
    }
}
