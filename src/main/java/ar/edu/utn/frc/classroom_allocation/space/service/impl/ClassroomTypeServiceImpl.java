package ar.edu.utn.frc.classroom_allocation.space.service.impl;

import ar.edu.utn.frc.classroom_allocation.common.exception.ResourceNotFoundException;
import ar.edu.utn.frc.classroom_allocation.space.model.ClassroomType;
import ar.edu.utn.frc.classroom_allocation.space.repository.ClassroomTypeRepository;
import ar.edu.utn.frc.classroom_allocation.space.service.ClassroomTypeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class ClassroomTypeServiceImpl implements ClassroomTypeService {

    private final ClassroomTypeRepository classroomTypeRepository;

    @Override
    public ClassroomType findById(Integer id) {
        log.debug("Fetching classroom type: id={}", id);
        return findExistingById(id);
    }

    @Override
    @Transactional(readOnly = true)
    public ClassroomType findDefault() {
        return classroomTypeRepository.findFirstByDeletedFalse()
                .orElseThrow(() -> {
                    log.warn("No ClassroomType found in database");
                    return new ResourceNotFoundException("No ClassroomType found");
                });
    }

    @Transactional(readOnly = true)
    protected ClassroomType findExistingById(Integer id) {
        return classroomTypeRepository.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> {
                    log.warn("ClassroomType not found: id={}", id);
                    return new ResourceNotFoundException("ClassroomType not found with id: " + id);
                });
    }

}
