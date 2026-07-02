package ar.edu.utn.frc.siga.academic.service.impl;

import ar.edu.utn.frc.siga.common.dto.FindOrCreateResult;
import ar.edu.utn.frc.siga.academic.model.AcademicPeriod;
import ar.edu.utn.frc.siga.academic.model.Commission;
import ar.edu.utn.frc.siga.academic.repository.CommissionRepository;
import ar.edu.utn.frc.siga.academic.service.CommissionService;
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

    @Override
    public Optional<Commission> findByCourseCodeAndCommissionNumberAndPeriodAndDeletedFalse(
            String courseCode, Integer commissionNumber, AcademicPeriod period) {
        log.debug("Finding commission: courseCode={}, commissionNumber={}, periodId={}",
                courseCode, commissionNumber, period.getId());
        return commissionRepository.findByCourseCodeAndCommissionNumberAndAcademicPeriodAndDeletedFalse(
                courseCode, commissionNumber, period);
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
                log.info("Creando Commission: course={}, commission={}, period={}",
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
}
