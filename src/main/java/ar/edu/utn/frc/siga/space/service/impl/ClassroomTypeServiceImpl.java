package ar.edu.utn.frc.siga.space.service.impl;

import ar.edu.utn.frc.siga.common.exception.ResourceNotFoundException;
import ar.edu.utn.frc.siga.common.util.Finder;
import ar.edu.utn.frc.siga.space.model.ClassroomType;
import ar.edu.utn.frc.siga.space.repository.ClassroomTypeRepository;
import ar.edu.utn.frc.siga.space.service.ClassroomTypeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class ClassroomTypeServiceImpl implements ClassroomTypeService {

    private final ClassroomTypeRepository classroomTypeRepository;

    @Override
    public ClassroomType findById(Long id) {
        log.debug("Buscando tipo de aula: id={}", id);
        return findExistingById(id);
    }

    @Override
    @Transactional
    public void activate(Long id) {
        classroomTypeRepository.restore(Finder.orThrow(classroomTypeRepository::findById, id, "ClassroomType"));
    }

    @Override
    @Transactional
    public void deactivate(Long id) {
        classroomTypeRepository.softDelete(Finder.orThrow(classroomTypeRepository::findById, id, "ClassroomType"));
    }

    private ClassroomType findExistingById(Long id) {
        return classroomTypeRepository.findActiveById(id)
                .orElseThrow(() -> {
                    log.warn("Tipo de aula no encontrado: id={}", id);
                    return ResourceNotFoundException.of("ClassroomType", id);
                });
    }

}
