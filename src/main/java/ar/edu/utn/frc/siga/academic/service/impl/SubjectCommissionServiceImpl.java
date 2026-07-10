package ar.edu.utn.frc.siga.academic.service.impl;

import ar.edu.utn.frc.siga.academic.dto.response.SubjectCommissionResponseDto;
import ar.edu.utn.frc.siga.academic.mapper.SubjectCommissionMapper;
import ar.edu.utn.frc.siga.academic.model.Commission;
import ar.edu.utn.frc.siga.academic.model.Subject;
import ar.edu.utn.frc.siga.academic.model.SubjectCommission;
import ar.edu.utn.frc.siga.academic.repository.CommissionRepository;
import ar.edu.utn.frc.siga.academic.repository.SubjectCommissionRepository;
import ar.edu.utn.frc.siga.academic.repository.SubjectRepository;
import ar.edu.utn.frc.siga.academic.service.SubjectCommissionService;
import ar.edu.utn.frc.siga.common.dto.FindOrCreateResult;
import ar.edu.utn.frc.siga.common.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class SubjectCommissionServiceImpl implements SubjectCommissionService {

    private final SubjectCommissionRepository subjectCommissionRepository;
    private final SubjectRepository subjectRepository;
    private final CommissionRepository commissionRepository;
    private final SubjectCommissionMapper subjectCommissionMapper;

    @Override
    @Transactional
    public FindOrCreateResult<SubjectCommissionResponseDto> findOrCreate(
            Long subjectId, Long commissionId, Integer enrolledCount) {
        Subject subject = requireSubject(subjectId);
        Commission commission = requireCommission(commissionId);
        return FindOrCreateResult.resolve(
                subjectCommissionRepository.findBySubjectAndCommissionAndDeletedFalse(subject, commission),
                () -> {
                    log.info("Creando SubjectCommission: subject={}, commission={}", subjectId, commissionId);
                    return subjectCommissionRepository.save(
                            SubjectCommission.builder()
                                    .subject(subject)
                                    .commission(commission)
                                    .enrolledCount(enrolledCount)
                                    .build());
                }
        ).map(subjectCommissionMapper::toDto);
    }

    private Subject requireSubject(Long id) {
        return subjectRepository.findById(id)
                .orElseThrow(() -> ResourceNotFoundException.of("Subject", id));
    }

    private Commission requireCommission(Long id) {
        return commissionRepository.findById(id)
                .orElseThrow(() -> ResourceNotFoundException.of("Commission", id));
    }
}
