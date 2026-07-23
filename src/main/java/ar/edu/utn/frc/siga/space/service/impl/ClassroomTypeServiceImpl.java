package ar.edu.utn.frc.siga.space.service.impl;

import ar.edu.utn.frc.siga.common.exception.ResourceNotFoundException;
import ar.edu.utn.frc.siga.space.model.ClassroomType;
import ar.edu.utn.frc.siga.space.repository.ClassroomTypeRepository;
import ar.edu.utn.frc.siga.space.service.ClassroomTypeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Implementación de {@link ClassroomTypeService}.
 */
@Slf4j
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class ClassroomTypeServiceImpl implements ClassroomTypeService {

    private final ClassroomTypeRepository classroomTypeRepository;

    @Override
    public ClassroomType findById(Integer id) {
        log.debug("Buscando tipo de aula: id={}", id);
        return findExistingById(id);
    }

    private ClassroomType findExistingById(Integer id) {
        return classroomTypeRepository.findById(id)
                .orElseThrow(() -> {
                    log.warn("Tipo de aula no encontrado: id={}", id);
                    return ResourceNotFoundException.of("ClassroomType", id);
                });
    }

}
