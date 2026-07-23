package ar.edu.utn.frc.siga.academic.service.impl;

import ar.edu.utn.frc.siga.academic.dto.response.SubjectCommissionResponseDto;
import ar.edu.utn.frc.siga.academic.mapper.SubjectCommissionMapper;
import ar.edu.utn.frc.siga.academic.model.Commission;
import ar.edu.utn.frc.siga.academic.model.Subject;
import ar.edu.utn.frc.siga.academic.repository.CommissionRepository;
import ar.edu.utn.frc.siga.academic.repository.SubjectCommissionRepository;
import ar.edu.utn.frc.siga.academic.repository.SubjectRepository;
import ar.edu.utn.frc.siga.academic.service.SubjectCommissionService;
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
public class SubjectCommissionServiceImpl implements SubjectCommissionService {

    private final SubjectCommissionRepository subjectCommissionRepository;
    private final SubjectRepository subjectRepository;
    private final CommissionRepository commissionRepository;
    private final SubjectCommissionMapper subjectCommissionMapper;

    @Override
    public SubjectCommissionResponseDto findBySubjectAndCommission(Long subjectId, Long commissionId) {
        Subject subject = requireSubject(subjectId);
        Commission commission = requireCommission(commissionId);
        return subjectCommissionRepository.findBySubjectAndCommission(subject, commission)
                .map(subjectCommissionMapper::toDto)
                .orElseThrow(() -> ResourceNotFoundException.of("SubjectCommission",
                        subjectId + "-" + commissionId));
    }

    private Subject requireSubject(Long id) {
        return subjectRepository.findById(id)
                .orElseThrow(() -> ResourceNotFoundException.of("Subject", id));
    }

    private Commission requireCommission(Long id) {
        return commissionRepository.findById(id)
                .orElseThrow(() -> ResourceNotFoundException.of("Commission", id));
    }

    @Override
    public List<SubjectCommissionResponseDto> findAll() {
        return subjectCommissionRepository.findAll().stream()
                .map(subjectCommissionMapper::toDto)
                .toList();
    }

    @Override
    public SubjectCommissionResponseDto findById(Long id) {
        return subjectCommissionMapper.toDto(subjectCommissionRepository.findById(id)
                .orElseThrow(() -> ResourceNotFoundException.of("SubjectCommission", id)));
    }
}
