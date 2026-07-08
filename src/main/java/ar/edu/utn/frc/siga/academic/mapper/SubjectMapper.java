package ar.edu.utn.frc.siga.academic.mapper;

import ar.edu.utn.frc.siga.academic.dto.response.SubjectResponseDto;
import ar.edu.utn.frc.siga.academic.model.Subject;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class SubjectMapper {

    private final StudyPlanMapper studyPlanMapper;

    public SubjectResponseDto toDto(Subject subject) {
        return SubjectResponseDto.builder()
                .id(subject.getId())
                .code(subject.getCode())
                .name(subject.getName())
                .term(subject.getTerm())
                .studyPlan(studyPlanMapper.toDto(subject.getStudyPlan()))
                .build();
    }
}