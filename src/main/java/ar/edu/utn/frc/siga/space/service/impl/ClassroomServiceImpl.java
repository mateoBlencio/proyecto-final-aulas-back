package ar.edu.utn.frc.siga.space.service.impl;

import ar.edu.utn.frc.siga.space.dto.ClassroomFilter;
import ar.edu.utn.frc.siga.space.dto.request.ClassroomRequestDto;
import ar.edu.utn.frc.siga.space.dto.response.ClassroomResponseDto;
import ar.edu.utn.frc.siga.common.exception.ResourceNotFoundException;
import ar.edu.utn.frc.siga.space.exception.SpaceDomainException;
import ar.edu.utn.frc.siga.space.mapper.ClassroomMapper;
import ar.edu.utn.frc.siga.common.util.Hashes;
import ar.edu.utn.frc.siga.space.model.Building;
import ar.edu.utn.frc.siga.space.model.Classroom;
import ar.edu.utn.frc.siga.space.model.ClassroomType;
import ar.edu.utn.frc.siga.space.repository.BuildingRepository;
import ar.edu.utn.frc.siga.space.repository.ClassroomRepository;
import ar.edu.utn.frc.siga.space.repository.ClassroomTypeRepository;
import ar.edu.utn.frc.siga.space.service.ClassroomService;
import ar.edu.utn.frc.siga.space.service.ClassroomTypeService;
import ar.edu.utn.frc.siga.space.service.command.ClassroomSyncCommand;
import ar.edu.utn.frc.siga.space.specification.ClassroomSpecification;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class ClassroomServiceImpl implements ClassroomService {

    private static final String DEFAULT_CLASSROOM_TYPE = "Normal";

    private final ClassroomRepository classroomRepository;
    private final BuildingRepository buildingRepository;
    private final ClassroomTypeService classroomTypeService;
    private final ClassroomTypeRepository classroomTypeRepository;
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

        validateCapacity(dto);

        Classroom entity = classroomMapper.toEntity(dto);
        entity.setBuilding(building);
        entity.setClassroomType(classroomType);

        Classroom saved = classroomRepository.save(entity);
        log.info("Aula creada: id={}, roomNumber={}", saved.getId(), saved.getRoomNumber());
        return classroomMapper.toDto(saved);
    }

    @Override
    public ClassroomResponseDto findById(Long id) {
        log.debug("Buscando aula por id={}", id);
        return classroomMapper.toDto(this.findExistingClassroomById(id));
    }

    @Override
    public List<ClassroomResponseDto> findAllAvailable() {
        log.debug("Listando todas las aulas disponibles");
        return classroomRepository.findAll().stream()
                .map(classroomMapper::toDto)
                .toList();
    }

    @Override
    public List<ClassroomResponseDto> findByIds(Collection<Long> ids) {
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
    public ClassroomResponseDto update(Long id, ClassroomRequestDto dto) {
        log.debug("Actualizando aula: id={}, roomNumber={}", id, dto.roomNumber());

        Classroom entity = this.findExistingClassroomById(id);
        Building building = findActiveBuilding(dto.buildingId());
        ClassroomType classroomType = classroomTypeService.findById(dto.classroomTypeId());

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
    public void delete(Long id) {
        log.debug("Eliminando (soft-delete) aula: id={}", id);
        Classroom classroom = this.findExistingClassroomById(id);
        classroom.setDeletedAt(Instant.now());
        classroomRepository.save(classroom);
        log.info("Aula eliminada: id={}", id);
    }

    @Override
    public ClassroomResponseDto findByRoomNumberAndBuilding(Integer roomNumber, Long buildingId) {
        Building building = findBuildingById(buildingId);
        return classroomMapper.toDto(classroomRepository.findByRoomNumberAndBuilding(roomNumber, building)
                .or(() -> fallbackByRoomNumberOnly(roomNumber, buildingId))
                .orElseThrow(() -> ResourceNotFoundException.of("Classroom", roomNumber)));
    }

    @Override
    public Optional<ClassroomResponseDto> findByRoomNumberAndBuildingCode(Integer roomNumber, Integer buildingCode) {
        return buildingRepository.findByBuildingCode(buildingCode)
                .flatMap(building -> classroomRepository.findByRoomNumberAndBuilding(roomNumber, building))
                .map(classroomMapper::toDto);
    }

    private Optional<Classroom> fallbackByRoomNumberOnly(Integer roomNumber, Long buildingId) {
        List<Classroom> matches = classroomRepository.findAllByRoomNumber(roomNumber);
        if (matches.size() != 1) {
            return Optional.empty();
        }
        Classroom found = matches.getFirst();
        log.warn("Aula '{}' no está en el edificio informado (buildingId={}); se usa la única "
                + "coincidencia por número, en buildingId={}", roomNumber, buildingId, found.getBuilding().getId());
        return Optional.of(found);
    }

    private Classroom findExistingClassroomById(Long id) {
        return classroomRepository.findById(id)
                .orElseThrow(() -> {
                    log.warn("Aula no encontrada: id={}", id);
                    return ResourceNotFoundException.of("Classroom", id);
                });
    }

    private Building findBuildingById(Long id) {
        return buildingRepository.findById(id)
                .orElseThrow(() -> ResourceNotFoundException.of("Building", id));
    }

    private Building findActiveBuilding(Long id) {
        Building building = findBuildingById(id);
        if (building.getDeletedAt() != null) {
            log.warn("Edificio no encontrado: id={}", id);
            throw ResourceNotFoundException.of("Building", id);
        }
        return building;
    }

    private void validateCapacity(ClassroomRequestDto dto) {
        if (dto.capacity() <= 0) {
            log.warn("Validación de capacidad fallida: capacity={}", dto.capacity());
            throw new SpaceDomainException("Capacity must be positive");
        }
    }

    @Override
    @Transactional
    public int syncClassrooms(List<ClassroomSyncCommand> commands) {
        Instant syncedAt = Instant.now();
        ClassroomType defaultType = null;
        Map<Integer, Building> buildingsByCode = buildingRepository.findAll().stream()
                .filter(building -> building.getBuildingCode() != null)
                .collect(Collectors.toMap(Building::getBuildingCode, Function.identity()));
        Map<ClassroomKey, Classroom> existing = classroomRepository.findAll().stream()
                .collect(Collectors.toMap(ClassroomServiceImpl::keyOf, Function.identity()));
        Set<ClassroomKey> incoming = new HashSet<>();
        int affected = 0;

        for (ClassroomSyncCommand command : commands) {
            if (command.roomNumber() == null || command.buildingCode() == null) {
                log.warn("Aula de SysAcad ignorada por clave incompleta: aula={}, edificio={}",
                        command.roomNumber(), command.buildingCode());
                continue;
            }
            Building building = buildingsByCode.get(command.buildingCode());
            if (building == null) {
                log.warn("Aula de SysAcad ignorada: no existe el edificio con codigo={}", command.buildingCode());
                continue;
            }

            ClassroomKey key = new ClassroomKey(building.getId(), command.roomNumber());
            incoming.add(key);
            String hash = Hashes.sha256Hex(command.capacity(), command.enabled());
            Classroom classroom = existing.get(key);

            if (classroom == null) {
                if (defaultType == null) {
                    defaultType = resolveDefaultClassroomType();
                }
                Classroom saved = classroomRepository.save(Classroom.builder()
                        .roomNumber(key.roomNumber())
                        .building(building)
                        .classroomType(defaultType)
                        .capacity(command.capacity())
                        .sysacadEnabled(command.enabled())
                        .syncedAt(syncedAt)
                        .sysacadHash(hash)
                        .build());
                existing.put(key, saved);
                affected++;
                continue;
            }
            if (isUpToDate(classroom, hash)) {
                continue;
            }
            // `classroomType` es local-owned: el sync nunca lo pisa en un update (§4.3).
            // Al crear sí se asigna un default (§ constraint NOT NULL), ver resolveDefaultClassroomType().
            classroom.setCapacity(command.capacity());
            classroom.setSysacadEnabled(command.enabled());
            classroom.setSyncedAt(syncedAt);
            classroom.setSysacadHash(hash);
            classroomRepository.save(classroom);
            affected++;
        }

        return affected + markAbsent(existing.values(), incoming, syncedAt);
    }

    private int markAbsent(Iterable<Classroom> existing, Set<ClassroomKey> incoming, Instant syncedAt) {
        int affected = 0;
        for (Classroom classroom : existing) {
            if (incoming.contains(keyOf(classroom)) || Boolean.FALSE.equals(classroom.getSysacadEnabled())) {
                continue;
            }
            classroom.setSysacadEnabled(false);
            classroom.setSyncedAt(syncedAt);
            classroomRepository.save(classroom);
            affected++;
            log.info("Aula marcada como no vigente en SysAcad: id={}", classroom.getId());
        }
        return affected;
    }

    private ClassroomType resolveDefaultClassroomType() {
        return classroomTypeRepository.findByDescriptionIgnoreCase(DEFAULT_CLASSROOM_TYPE)
                .orElseThrow(() -> new IllegalStateException(
                        "Falta el tipo de aula por defecto '" + DEFAULT_CLASSROOM_TYPE + "' (seed de data.sql)"));
    }

    private static ClassroomKey keyOf(Classroom classroom) {
        return new ClassroomKey(classroom.getBuilding().getId(), classroom.getRoomNumber());
    }

    private static boolean isUpToDate(Classroom classroom, String hash) {
        return hash.equals(classroom.getSysacadHash()) && Boolean.TRUE.equals(classroom.getSysacadEnabled());
    }

    private record ClassroomKey(Long buildingId, Integer roomNumber) {}
}
