package ar.edu.utn.frc.siga.roomrequest.validator;

import ar.edu.utn.frc.siga.common.exception.ResourceNotFoundException;
import ar.edu.utn.frc.siga.common.util.Maps;
import ar.edu.utn.frc.siga.space.dto.response.ClassroomResponseDto;
import ar.edu.utn.frc.siga.space.service.ClassroomService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/** Sólo verifica que las aulas de preferencia existan (no capacidad ni disponibilidad: son preferencias). */
@Component
@RequiredArgsConstructor
public class ClassroomReferenceValidator {

    private final ClassroomService classroomService;

    public void requireExist(Collection<Long> classroomIds) {
        Set<Long> ids = new LinkedHashSet<>(classroomIds);
        if (ids.isEmpty()) {
            return;
        }
        Map<Long, ClassroomResponseDto> byId =
                Maps.byId(classroomService.findByIds(ids), ClassroomResponseDto::id);
        for (Long id : ids) {
            if (!byId.containsKey(id)) {
                throw ResourceNotFoundException.of("Classroom", id);
            }
        }
    }
}
