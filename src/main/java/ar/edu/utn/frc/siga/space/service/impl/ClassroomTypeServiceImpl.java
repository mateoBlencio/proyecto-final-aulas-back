package ar.edu.utn.frc.siga.space.service.impl;

import ar.edu.utn.frc.siga.common.exception.ResourceNotFoundException;
import ar.edu.utn.frc.siga.space.exception.SpaceDomainException;
import ar.edu.utn.frc.siga.space.model.ClassroomType;
import ar.edu.utn.frc.siga.space.repository.ClassroomTypeRepository;
import ar.edu.utn.frc.siga.space.service.ClassroomTypeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class ClassroomTypeServiceImpl implements ClassroomTypeService {

    private final ClassroomTypeRepository classroomTypeRepository;

    @Value("${siga.space.default-classroom-type:Normal}")
    private String defaultClassroomTypeDescription;

    @Override
    public ClassroomType findById(Integer id) {
        log.debug("Buscando tipo de aula: id={}", id);
        return findExistingById(id);
    }

    @Override
    public ClassroomType findDefault() {
        return classroomTypeRepository.findByDescriptionIgnoreCase(defaultClassroomTypeDescription)
                .orElseThrow(() -> {
                    log.warn("Tipo de aula por defecto no encontrado: description={}", defaultClassroomTypeDescription);
                    return new SpaceDomainException(
                            "No existe un tipo de aula por defecto con descripción '"
                                    + defaultClassroomTypeDescription + "'.");
                });
    }

    private ClassroomType findExistingById(Integer id) {
        return classroomTypeRepository.findById(id)
                .orElseThrow(() -> {
                    log.warn("Tipo de aula no encontrado: id={}", id);
                    return ResourceNotFoundException.of("ClassroomType", id);
                });
    }

}
