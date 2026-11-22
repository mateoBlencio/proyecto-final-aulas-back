package ar.edu.utn.frc.siga.space.permission;

import ar.edu.utn.frc.siga.academic.dto.response.SpecialtyResponseDto;
import ar.edu.utn.frc.siga.academic.dto.response.StudyPlanResponseDto;
import ar.edu.utn.frc.siga.academic.dto.response.SubjectResponseDto;
import ar.edu.utn.frc.siga.academic.service.SubjectService;
import ar.edu.utn.frc.siga.space.model.PermissionTargetKind;
import java.util.Collection;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class SubjectPermissionTargetResolver implements PermissionTargetResolver {

    private final SubjectService subjectService;

    @Override
    public PermissionTargetKind kind() {
        return PermissionTargetKind.SUBJECT;
    }

    @Override
    public Map<Long, TargetInfo> resolve(Collection<Long> targetIds) {
        if (targetIds.isEmpty()) {
            return Map.of();
        }
        return subjectService.findByIdsIncludingDeactivated(targetIds).stream()
                .collect(Collectors.toMap(SubjectResponseDto::id, SubjectPermissionTargetResolver::toInfo, (a, b) -> a));
    }

    private static TargetInfo toInfo(SubjectResponseDto subject) {
        StudyPlanResponseDto plan = subject.studyPlan();
        SpecialtyResponseDto specialty = plan == null ? null : plan.specialty();
        return new TargetInfo(
                subject.name(),
                specialty == null ? null : specialty.name(),
                specialty == null ? null : specialty.abbreviation(),
                specialty == null ? null : specialty.specialtyCode(),
                plan == null ? null : plan.planCode());
    }
}
