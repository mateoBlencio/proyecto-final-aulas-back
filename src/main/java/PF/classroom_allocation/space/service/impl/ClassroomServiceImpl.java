package PF.classroom_allocation.space.service.impl;

import PF.classroom_allocation.space.dto.ClassroomFilter;
import PF.classroom_allocation.space.dto.request.ClassroomRequestDTO;
import PF.classroom_allocation.space.dto.response.ClassroomResponseDTO;
import PF.classroom_allocation.space.exception.ResourceNotFoundException;
import PF.classroom_allocation.space.exception.SpaceDomainException;
import PF.classroom_allocation.space.mapper.ClassroomMapper;
import PF.classroom_allocation.space.model.Building;
import PF.classroom_allocation.space.model.Classroom;
import PF.classroom_allocation.space.model.ClassroomType;
import PF.classroom_allocation.space.repository.ClassroomRepository;
import PF.classroom_allocation.space.service.BuildingService;
import PF.classroom_allocation.space.service.ClassroomService;
import PF.classroom_allocation.space.service.ClassroomTypeService;
import PF.classroom_allocation.space.specification.ClassroomSpecification;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ClassroomServiceImpl implements ClassroomService {

    private final ClassroomRepository classroomRepository;
    private final BuildingService buildingService;
    private final ClassroomTypeService classroomTypeService;
    private final ClassroomMapper classroomMapper;

    @Override
    @Transactional
    public ClassroomResponseDTO create(ClassroomRequestDTO dto) {
        Building building = buildingService.findById(dto.buildingId());

        ClassroomType classroomType = classroomTypeService.findById(dto.classroomTypeId());

        if (classroomRepository.findByRoomNumberAndDeletedFalse(dto.roomNumber()).isPresent()) {
            throw new SpaceDomainException("Classroom roomNumber already exists: " + dto.roomNumber());
        }

        validateFloor(dto, building);
        validateCapacity(dto);

        Classroom entity = classroomMapper.toEntity(dto);
        entity.setBuilding(building);
        entity.setClassroomType(classroomType);

        Classroom saved = classroomRepository.save(entity);
        return classroomMapper.toResponseDto(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public ClassroomResponseDTO findById(Integer id) {
        return classroomMapper.toResponseDto(this.findExistingClassroomById(id));
    }

    @Override
    @Transactional(readOnly = true)
    public Page<ClassroomResponseDTO> findAll(ClassroomFilter filter, Pageable pageable) {
        return classroomRepository.findAll(ClassroomSpecification.withFilter(filter), pageable)
                .map(classroomMapper::toResponseDto);
    }

    @Override
    @Transactional
    public ClassroomResponseDTO update(Integer id, ClassroomRequestDTO dto) {
        Classroom entity = this.findExistingClassroomById(id);

        Building building = buildingService.findById(dto.buildingId());

        ClassroomType classroomType = classroomTypeService.findById(dto.classroomTypeId());

        validateFloor(dto, building);
        validateCapacity(dto);

        classroomMapper.updateEntity(entity, dto);
        entity.setBuilding(building);
        entity.setClassroomType(classroomType);

        Classroom saved = classroomRepository.save(entity);
        return classroomMapper.toResponseDto(saved);
    }

    @Override
    @Transactional
    public void delete(Integer id) {
        Classroom classroom = this.findExistingClassroomById(id);
        classroom.setDeleted(true);
        classroomRepository.save(classroom);
    }

    @Transactional(readOnly = true)
    protected Classroom findExistingClassroomById(Integer id) {
        return classroomRepository.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> new ResourceNotFoundException("Classroom not found with id: " + id));
    }

    private void validateFloor(ClassroomRequestDTO dto, Building building) {
        if (dto.floor() > building.getFloorCount()) {
            throw new SpaceDomainException(
                    "Floor " + dto.floor() + " exceeds building floor count " + building.getFloorCount());
        }
    }

    private void validateCapacity(ClassroomRequestDTO dto) {
        if (dto.capacity() <= 0) {
            throw new SpaceDomainException("Capacity must be positive");
        }
    }

}
