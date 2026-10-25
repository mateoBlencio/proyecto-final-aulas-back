package ar.edu.utn.frc.siga.academic.service.impl;

import ar.edu.utn.frc.siga.audit.AuditOperation;
import ar.edu.utn.frc.siga.academic.dto.SubjectCommissionFilter;
import ar.edu.utn.frc.siga.academic.dto.response.SubjectCommissionResponseDto;
import ar.edu.utn.frc.siga.academic.mapper.SubjectCommissionMapper;
import ar.edu.utn.frc.siga.academic.model.Commission;
import ar.edu.utn.frc.siga.academic.model.StudyPlan;
import ar.edu.utn.frc.siga.academic.model.Subject;
import ar.edu.utn.frc.siga.academic.model.SubjectCommission;
import ar.edu.utn.frc.siga.academic.model.SubjectCommissionId;
import ar.edu.utn.frc.siga.academic.repository.CommissionRepository;
import ar.edu.utn.frc.siga.academic.repository.SubjectCommissionRepository;
import ar.edu.utn.frc.siga.academic.repository.SubjectRepository;
import ar.edu.utn.frc.siga.academic.service.SubjectCommissionService;
import ar.edu.utn.frc.siga.academic.specification.SubjectCommissionSpecification;
import ar.edu.utn.frc.siga.common.exception.ResourceNotFoundException;
import ar.edu.utn.frc.siga.common.util.Finder;
import java.util.List;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
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
        return subjectCommissionRepository.findBySubjectAndCommissionAndDeletedAtIsNull(subject, commission)
                .map(subjectCommissionMapper::toDto)
                .orElseThrow(() -> ResourceNotFoundException.of("SubjectCommission",
                        subjectId + "-" + commissionId));
    }

    @Override
    @Transactional
    @AuditOperation("Reactivación de materia-comisión")
    public void activate(SubjectCommissionId id) {
        subjectCommissionRepository.restore(
                Finder.orThrow(subjectCommissionRepository::findById, id, "SubjectCommission"));
    }

    @Override
    @Transactional
    @AuditOperation("Baja de materia-comisión")
    public void deactivate(SubjectCommissionId id) {
        subjectCommissionRepository.softDelete(
                Finder.orThrow(subjectCommissionRepository::findById, id, "SubjectCommission"));
    }

    private Subject requireSubject(Long id) {
        return subjectRepository.findById(id)
                .filter(Subject::isAvailable)
                .orElseThrow(() -> ResourceNotFoundException.of("Subject", id));
    }

    private Commission requireCommission(Long id) {
        return commissionRepository.findById(id)
                .filter(Commission::isAvailable)
                .orElseThrow(() -> ResourceNotFoundException.of("Commission", id));
    }

    @Override
    public SubjectCommissionResponseDto findByCommissionAndSubjectCode(Long commissionId, Integer subjectCode,
            Integer specialtyCode, Integer studyPlanCode) {
        List<SubjectCommission> candidates = subjectCommissionRepository
                .findByCommission_IdAndSubject_CodeAndDeletedAtIsNull(commissionId, subjectCode);
        List<SubjectCommission> exact = candidates.stream()
                .filter(link -> isOfStudyPlan(link.getSubject().getStudyPlan(), specialtyCode, studyPlanCode))
                .toList();
        List<SubjectCommission> chosen = exact.isEmpty() ? candidates : exact;
        if (chosen.size() == 1) {
            return subjectCommissionMapper.toDto(chosen.getFirst());
        }
        if (chosen.size() > 1) {
            log.warn("Más de una materia con código {} en la comisión {} y ninguna coincide con especialidad={}, "
                    + "plan={}: no se resuelve el link", subjectCode, commissionId, specialtyCode, studyPlanCode);
        }
        throw ResourceNotFoundException.of("SubjectCommission", commissionId + "-" + subjectCode);
    }

    private static boolean isOfStudyPlan(StudyPlan studyPlan, Integer specialtyCode, Integer studyPlanCode) {
        return Objects.equals(studyPlan.getPlanCode(), studyPlanCode)
                && Objects.equals(studyPlan.getSpecialty().getSpecialtyCode(), specialtyCode);
    }

    @Override
    public Page<SubjectCommissionResponseDto> findAll(SubjectCommissionFilter filter, Pageable pageable,
            boolean includeDeactivated) {
        return subjectCommissionRepository.findAll(
                        SubjectCommissionSpecification.withFilter(filter)
                                .and(includeDeactivated
                                        ? Specification.<SubjectCommission>unrestricted()
                                        : SubjectCommissionSpecification.available()),
                        pageable)
                .map(subjectCommissionMapper::toDto);
    }
}
