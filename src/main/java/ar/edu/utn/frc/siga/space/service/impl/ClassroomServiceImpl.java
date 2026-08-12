package ar.edu.utn.frc.siga.space.service.impl;

import ar.edu.utn.frc.siga.space.dto.ClassroomFilter;
import ar.edu.utn.frc.siga.space.dto.request.ClassroomRequestDto;
import ar.edu.utn.frc.siga.space.dto.response.ClassroomResponseDto;
import ar.edu.utn.frc.siga.common.exception.ResourceNotFoundException;
import ar.edu.utn.frc.siga.space.exception.SpaceDomainException;
import ar.edu.utn.frc.siga.space.mapper.ClassroomMapper;
import ar.edu.utn.frc.siga.space.model.Building;
import ar.edu.utn.frc.siga.space.model.Classroom;
import ar.edu.utn.frc.siga.space.model.ClassroomType;
import ar.edu.utn.frc.siga.space.repository.BuildingRepository;
import ar.edu.utn.frc.siga.space.repository.ClassroomRepository;
import ar.edu.utn.frc.siga.space.service.ClassroomService;
import ar.edu.utn.frc.siga.space.service.ClassroomTypeService;
import ar.edu.utn.frc.siga.space.specification.ClassroomSpecification;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class ClassroomServiceImpl implements ClassroomService {

    private final ClassroomRepository classroomRepository;
    private final BuildingRepository buildingRepository;
    private final ClassroomTypeService classroomTypeService;
    private final ClassroomMapper classroomMapper;

    @Override
    @Transactional
    public ClassroomResponseDto create(ClassroomRequestDto dto) {
        log.debug("Creando aula: roomNumber={}, buildingId={}, classroomTypeId={}",
                dto.roomNumber(), dto.buildingId(), dto.classroomTypeId());

        Building building = findActiveBuilding(dto.buildingId());
        ClassroomType classroomType = classroomTypeService.findById(dto.classroomTypeId());

        if (classroomRepository.findByRoomNumber(dto.roomNumber()).isPresent()) {
            log.warn("Creación de aula rechazada: roomNumber '{}' ya existe", dto.roomNumber());
            throw new SpaceDomainException("Classroom roomNumber already exists: " + dto.roomNumber());
        }

        validateFloor(dto, building);
        validateCapacity(dto);

        Classroom entity = classroomMapper.toEntity(dto);
        entity.setBuilding(building);
        entity.setClassroomType(classroomType);

        Classroom saved = classroomRepository.save(entity);
        log.info("Aula creada: id={}, roomNumber={}", saved.getId(), saved.getRoomNumber());
        return classroomMapper.toDto(saved);
    }

    @Override
    public ClassroomResponseDto findById(Integer id) {
        log.debug("Buscando aula por id={}", id);
        return classroomMapper.toDto(this.findExistingClassroomById(id));
    }

    @Override
    public List<ClassroomResponseDto> findAllAvailable() {
        log.debug("Listando todas las aulas disponibles");
        return classroomRepository.findByAvailableTrue().stream()
                .map(classroomMapper::toDto)
                .toList();
    }

    @Override
    public List<ClassroomResponseDto> findByIds(Collection<Integer> ids) {
        log.debug("Buscando aulas por ids: {}", ids);
        return classroomRepository.findAllById(ids).stream()
                .map(classroomMapper::toDto)
                .toList();
    }

    @Override
    public Page<ClassroomResponseDto> findAll(ClassroomFilter filter, Pageable pageable) {
        log.debug("Listando aulas: filter={}, page={}, size={}", filter, pageable.getPageNumber(), pageable.getPageSize());
        return classroomRepository.findAll(ClassroomSpecification.withFilter(filter), pageable)
                .map(classroomMapper::toDto);
    }

    @Override
    @Transactional
    public ClassroomResponseDto update(Integer id, ClassroomRequestDto dto) {
        log.debug("Actualizando aula: id={}, roomNumber={}", id, dto.roomNumber());

        Classroom entity = this.findExistingClassroomById(id);
        Building building = findActiveBuilding(dto.buildingId());
        ClassroomType classroomType = classroomTypeService.findById(dto.classroomTypeId());

        validateFloor(dto, building);
        validateCapacity(dto);

        classroomMapper.updateEntity(entity, dto);
        entity.setBuilding(building);
        entity.setClassroomType(classroomType);

        Classroom saved = classroomRepository.save(entity);
        log.info("Aula actualizada: id={}, roomNumber={}", saved.getId(), saved.getRoomNumber());
        return classroomMapper.toDto(saved);
    }

    @Override
    @Transactional
    public void delete(Integer id) {
        log.debug("Eliminando (soft-delete) aula: id={}", id);
        Classroom classroom = this.findExistingClassroomById(id);
        classroom.setDeleted(true);
        classroomRepository.save(classroom);
        log.info("Aula eliminada: id={}", id);
    }

    @Override
    public ClassroomResponseDto findByRoomNumberAndBuilding(String roomNumber, Integer buildingId) {
        Building building = requireBuilding(buildingId);
        return classroomMapper.toDto(classroomRepository.findByRoomNumberAndBuilding(roomNumber, building)
                .or(() -> fallbackByRoomNumberOnly(roomNumber, buildingId))
                .orElseThrow(() -> ResourceNotFoundException.of("Classroom", roomNumber)));
    }

    private Optional<Classroom> fallbackByRoomNumberOnly(String roomNumber, Integer buildingId) {
        List<Classroom> matches = classroomRepository.findAllByRoomNumber(roomNumber);
        if (matches.size() != 1) {
            return Optional.empty();
        }
        Classroom found = matches.getFirst();
        log.warn("Aula '{}' no está en el edificio informado (buildingId={}); se usa la única "
                + "coincidencia por número, en buildingId={}", roomNumber, buildingId, found.getBuilding().getId());
        return Optional.of(found);
    }

    private Classroom findExistingClassroomById(Integer id) {
        return classroomRepository.findById(id)
                .orElseThrow(() -> {
                    log.warn("Aula no encontrada: id={}", id);
                    return ResourceNotFoundException.of("Classroom", id);
                });
    }

    private Building findActiveBuilding(Integer id) {
        return buildingRepository.findById(id)
                .filter(Building::getActive)
                .orElseThrow(() -> {
                    log.warn("Edificio no encontrado: id={}", id);
                    return ResourceNotFoundException.of("Building", id);
                });
    }

    private Building requireBuilding(Integer id) {
        return buildingRepository.findById(id)
                .orElseThrow(() -> ResourceNotFoundException.of("Building", id));
    }

    private void validateFloor(ClassroomRequestDto dto, Building building) {
        if (dto.floor() > building.getFloorCount()) {
            log.warn("Validación de piso fallida: floor={} excede floorCount={} del edificio, buildingId={}",
                    dto.floor(), building.getFloorCount(), building.getId());
            throw new SpaceDomainException(
                    "Floor " + dto.floor() + " exceeds building floor count " + building.getFloorCount());
        }
    }

    private void validateCapacity(ClassroomRequestDto dto) {
        if (dto.capacity() <= 0) {
            log.warn("Validación de capacidad fallida: capacity={}", dto.capacity());
            throw new SpaceDomainException("Capacity must be positive");
        }
    }

}
