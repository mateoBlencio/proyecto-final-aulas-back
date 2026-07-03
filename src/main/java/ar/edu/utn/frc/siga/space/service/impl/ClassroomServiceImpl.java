package ar.edu.utn.frc.siga.space.service.impl;

import ar.edu.utn.frc.siga.common.dto.FindOrCreateResult;
import ar.edu.utn.frc.siga.space.dto.ClassroomFilter;
import ar.edu.utn.frc.siga.space.dto.request.ClassroomRequestDTO;
import ar.edu.utn.frc.siga.space.dto.response.ClassroomResponseDTO;
import ar.edu.utn.frc.siga.common.exception.ResourceNotFoundException;
import ar.edu.utn.frc.siga.space.exception.SpaceDomainException;
import ar.edu.utn.frc.siga.space.mapper.ClassroomMapper;
import ar.edu.utn.frc.siga.space.model.Building;
import ar.edu.utn.frc.siga.space.model.Classroom;
import ar.edu.utn.frc.siga.space.model.ClassroomType;
import ar.edu.utn.frc.siga.space.repository.ClassroomRepository;
import ar.edu.utn.frc.siga.space.service.BuildingService;
import ar.edu.utn.frc.siga.space.service.ClassroomService;
import ar.edu.utn.frc.siga.space.service.ClassroomTypeService;
import ar.edu.utn.frc.siga.space.specification.ClassroomSpecification;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class ClassroomServiceImpl implements ClassroomService {

    private final ClassroomRepository classroomRepository;
    private final BuildingService buildingService;
    private final ClassroomTypeService classroomTypeService;
    private final ClassroomMapper classroomMapper;

    @Override
    @Transactional
    public ClassroomResponseDTO create(ClassroomRequestDTO dto) {
        log.debug("Creating classroom: roomNumber={}, buildingId={}, classroomTypeId={}",
                dto.roomNumber(), dto.buildingId(), dto.classroomTypeId());

        Building building = buildingService.findById(dto.buildingId());
        ClassroomType classroomType = classroomTypeService.findById(dto.classroomTypeId());

        if (classroomRepository.findByRoomNumberAndDeletedFalse(dto.roomNumber()).isPresent()) {
            log.warn("Classroom creation rejected: roomNumber '{}' already exists", dto.roomNumber());
            throw new SpaceDomainException("Classroom roomNumber already exists: " + dto.roomNumber());
        }

        validateFloor(dto, building);
        validateCapacity(dto);

        Classroom entity = classroomMapper.toEntity(dto);
        entity.setBuilding(building);
        entity.setClassroomType(classroomType);

        Classroom saved = classroomRepository.save(entity);
        log.info("Classroom created: id={}, roomNumber={}", saved.getId(), saved.getRoomNumber());
        return classroomMapper.toResponseDto(saved);
    }

    @Override
    public ClassroomResponseDTO findById(Integer id) {
        log.debug("Fetching classroom by id={}", id);
        return classroomMapper.toResponseDto(this.findExistingClassroomById(id));
    }

    @Override
    public Classroom requireById(Integer id) {
        log.debug("Fetching classroom entity by id={}", id);
        return this.findExistingClassroomById(id);
    }

    @Override
    public Page<ClassroomResponseDTO> findAll(ClassroomFilter filter, Pageable pageable) {
        log.debug("Listing classrooms: filter={}, page={}, size={}", filter, pageable.getPageNumber(), pageable.getPageSize());
        return classroomRepository.findAll(ClassroomSpecification.withFilter(filter), pageable)
                .map(classroomMapper::toResponseDto);
    }

    @Override
    @Transactional
    public ClassroomResponseDTO update(Integer id, ClassroomRequestDTO dto) {
        log.debug("Updating classroom: id={}, roomNumber={}", id, dto.roomNumber());

        Classroom entity = this.findExistingClassroomById(id);
        Building building = buildingService.findById(dto.buildingId());
        ClassroomType classroomType = classroomTypeService.findById(dto.classroomTypeId());

        validateFloor(dto, building);
        validateCapacity(dto);

        classroomMapper.updateEntity(entity, dto);
        entity.setBuilding(building);
        entity.setClassroomType(classroomType);

        Classroom saved = classroomRepository.save(entity);
        log.info("Classroom updated: id={}, roomNumber={}", saved.getId(), saved.getRoomNumber());
        return classroomMapper.toResponseDto(saved);
    }

    @Override
    @Transactional
    public void delete(Integer id) {
        log.debug("Soft-deleting classroom: id={}", id);
        Classroom classroom = this.findExistingClassroomById(id);
        classroom.setDeleted(true);
        classroomRepository.save(classroom);
        log.info("Classroom deleted: id={}", id);
    }

    protected Classroom findExistingClassroomById(Integer id) {
        return classroomRepository.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> {
                    log.warn("Classroom not found: id={}", id);
                    return new ResourceNotFoundException("Classroom not found with id: " + id);
                });
    }

    private void validateFloor(ClassroomRequestDTO dto, Building building) {
        if (dto.floor() > building.getFloorCount()) {
            log.warn("Floor validation failed: floor={} exceeds building floorCount={}, buildingId={}",
                    dto.floor(), building.getFloorCount(), building.getId());
            throw new SpaceDomainException(
                    "Floor " + dto.floor() + " exceeds building floor count " + building.getFloorCount());
        }
    }

    @Override
    @Transactional
    public FindOrCreateResult<Classroom> findOrCreate(String roomNumber, Building building, Integer enrolledCount) {
        return classroomRepository.findByRoomNumberAndBuildingAndDeletedFalse(roomNumber, building)
                .map(found -> new FindOrCreateResult<>(found, false))
                .orElseGet(() -> {
                    log.warn("Creating Classroom with provisional data: roomNumber={}, buildingId={}",
                        roomNumber, building.getId());
                    Classroom created = classroomRepository.save(
                        Classroom.builder()
                            .roomNumber(roomNumber)
                            .building(building)
                            .floor(0)
                            .capacity(enrolledCount != null && enrolledCount > 0 ? enrolledCount : 1)
                            .classroomType(classroomTypeService.findDefault())
                            .build()
                    );
                    return new FindOrCreateResult<>(created, true);
                });
    }

    private void validateCapacity(ClassroomRequestDTO dto) {
        if (dto.capacity() <= 0) {
            log.warn("Capacity validation failed: capacity={}", dto.capacity());
            throw new SpaceDomainException("Capacity must be positive");
        }
    }

}
