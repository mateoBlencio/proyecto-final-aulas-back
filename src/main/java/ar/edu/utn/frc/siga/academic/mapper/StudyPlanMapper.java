package ar.edu.utn.frc.siga.academic.mapper;

import ar.edu.utn.frc.siga.academic.dto.response.StudyPlanResponseDto;
import ar.edu.utn.frc.siga.academic.model.StudyPlan;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class StudyPlanMapper {

    private final SpecialtyMapper specialtyMapper;

    public StudyPlanResponseDto toDto(StudyPlan studyPlan) {
        if (studyPlan == null) {
            return null;
        }
        return StudyPlanResponseDto.builder()
                .planCode(studyPlan.getPlanCode())
                .specialty(specialtyMapper.toDto(studyPlan.getSpecialty()))
                .build();
    }
}