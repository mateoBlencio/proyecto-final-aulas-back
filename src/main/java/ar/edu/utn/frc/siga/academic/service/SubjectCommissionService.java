package ar.edu.utn.frc.siga.academic.service;

import ar.edu.utn.frc.siga.academic.dto.response.SubjectCommissionResponseDto;

import java.util.List;

import org.springframework.modulith.NamedInterface;

@NamedInterface("api")
public interface SubjectCommissionService {

    SubjectCommissionResponseDto findBySubjectAndCommission(Long subjectId, Long commissionId);

    List<SubjectCommissionResponseDto> findAll();

    List<SubjectCommissionResponseDto> findBySubjectId(Long subjectId);
}
