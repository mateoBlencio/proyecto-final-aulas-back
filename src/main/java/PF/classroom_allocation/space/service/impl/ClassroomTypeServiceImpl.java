package PF.classroom_allocation.space.service.impl;

import PF.classroom_allocation.space.exception.ResourceNotFoundException;
import PF.classroom_allocation.space.model.ClassroomType;
import PF.classroom_allocation.space.repository.ClassroomTypeRepository;
import PF.classroom_allocation.space.service.ClassroomTypeService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ClassroomTypeServiceImpl implements ClassroomTypeService {

    private final ClassroomTypeRepository classroomTypeRepository;

    @Override
    public ClassroomType findById(Integer id) {
        return findExistingById(id);
    }

    @Transactional(readOnly = true)
    protected ClassroomType findExistingById(Integer id) {
        return classroomTypeRepository.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> new ResourceNotFoundException("ClassroomType not found with id: " + id));
    }

}
