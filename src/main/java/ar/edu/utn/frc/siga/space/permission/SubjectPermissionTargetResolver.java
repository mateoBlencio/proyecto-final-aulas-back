package ar.edu.utn.frc.siga.space.permission;

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
    public Map<Long, String> resolveNames(Collection<Long> targetIds) {
        if (targetIds.isEmpty()) {
            return Map.of();
        }
        return subjectService.findByIds(targetIds).stream()
                .collect(Collectors.toMap(SubjectResponseDto::id, SubjectResponseDto::name, (a, b) -> a));
    }
}
