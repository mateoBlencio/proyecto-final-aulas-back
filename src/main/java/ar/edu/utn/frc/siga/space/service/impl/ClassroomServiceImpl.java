package ar.edu.utn.frc.siga.space.service.impl;

import ar.edu.utn.frc.siga.audit.AuditOperation;
import ar.edu.utn.frc.siga.space.dto.ClassroomFilter;
import ar.edu.utn.frc.siga.space.dto.request.ClassroomDetailsUpdateDto;
import ar.edu.utn.frc.siga.space.dto.request.ClassroomRequestDto;
import ar.edu.utn.frc.siga.space.dto.response.ClassroomListItemDto;
import ar.edu.utn.frc.siga.space.dto.response.ClassroomResponseDto;
import ar.edu.utn.frc.siga.common.exception.ResourceNotFoundException;
import ar.edu.utn.frc.siga.common.search.FuzzySearch;
import ar.edu.utn.frc.siga.common.security.BuildingScopeResolver;
import ar.edu.utn.frc.siga.common.security.Permission;
import ar.edu.utn.frc.siga.space.exception.SpaceDomainException;
import ar.edu.utn.frc.siga.space.mapper.ClassroomListComposer;
import ar.edu.utn.frc.siga.space.mapper.ClassroomMapper;
import ar.edu.utn.frc.siga.common.util.Hashes;
import ar.edu.utn.frc.siga.common.util.SyncGuard;
import ar.edu.utn.frc.siga.space.model.Building;
import ar.edu.utn.frc.siga.space.model.Classroom;
import ar.edu.utn.frc.siga.space.model.ClassroomResource;
import ar.edu.utn.frc.siga.space.model.ClassroomType;
import ar.edu.utn.frc.siga.space.model.PermissionMode;
import ar.edu.utn.frc.siga.space.model.ClassroomPermission;
import ar.edu.utn.frc.siga.space.model.PermissionTargetKind;
import ar.edu.utn.frc.siga.space.dto.request.ClassroomPermissionTargetRequestDto;
import ar.edu.utn.frc.siga.space.dto.response.ClassroomSubjectPermissionDto;
import ar.edu.utn.frc.siga.space.repository.BuildingRepository;
import ar.edu.utn.frc.siga.space.repository.ClassroomPermissionRepository;
import ar.edu.utn.frc.siga.space.repository.ClassroomRepository;
import ar.edu.utn.frc.siga.space.repository.ClassroomResourceRepository;
import ar.edu.utn.frc.siga.space.repository.ClassroomTypeRepository;
import ar.edu.utn.frc.siga.space.service.ClassroomService;
import ar.edu.utn.frc.siga.space.service.ClassroomTypeService;
import ar.edu.utn.frc.siga.space.service.command.ClassroomSyncCommand;
import ar.edu.utn.frc.siga.space.specification.ClassroomListSort;
import ar.edu.utn.frc.siga.space.specification.ClassroomSpecification;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
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

    private final ClassroomRepository classroomRepository;
    private final BuildingRepository buildingRepository;
    private final ClassroomTypeService classroomTypeService;
    private final ClassroomTypeRepository classroomTypeRepository;
    private final ClassroomMapper classroomMapper;
    private final ClassroomListComposer classroomListComposer;
    private final ClassroomFeatureWriter classroomFeatureWriter;
    private final BuildingScopeResolver buildingScopeResolver;
    private final ScopedClassroomFinder scopedClassroom;
    private final ClassroomPermissionRepository classroomPermissionRepository;
    private final ClassroomResourceRepository classroomResourceRepository;

    @Override
    @Transactional
    @AuditOperation("Alta de aula")
    public ClassroomResponseDto create(ClassroomRequestDto dto) {
        log.debug("Creando aula: roomNumber={}, buildingId={}, classroomTypeId={}",
                dto.roomNumber(), dto.buildingId(), dto.classroomTypeId());

        Building building = findActiveBuilding(dto.buildingId());
        buildingScopeResolver.requireAccess(Permission.CLASSROOM_CREATE, building.getId());
        ClassroomType classroomType = classroomTypeService.findById(dto.classroomTypeId());

        requireRoomNumberFree(dto.roomNumber(), building, null);

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
        return classroomMapper.toDto(scopedClassroom.requireActiveInScope(id, Permission.CLASSROOM_READ));
    }

    @Override
    public List<ClassroomResponseDto> findAllAvailable() {
        log.debug("Listando todas las aulas disponibles");
        return scopedClassroom.findAllActiveInScope(Permission.CLASSROOM_READ).stream()
                .map(classroomMapper::toDto)
                .toList();
    }

    @Override
    public List<ClassroomResponseDto> findByIds(Collection<Long> ids) {
        log.debug("Buscando aulas por ids: {}", ids);
        return classroomRepository.findAllById(ids).stream()
                .filter(Classroom::isAvailable)
                .map(classroomMapper::toDto)
                .toList();
    }

    @Override
    public List<ClassroomResponseDto> findByIdsIncludingDeactivated(Collection<Long> ids) {
        return classroomRepository.findAllById(ids).stream()
                .map(classroomMapper::toDto)
                .toList();
    }

    @Override
    public Set<Long> findIdsWithResourceAtLeast(String resourceTypeName, int minQuantity) {
        log.debug("Buscando aulas con recurso: resourceTypeName={}, minQuantity={}", resourceTypeName, minQuantity);
        return classroomResourceRepository.findClassroomIdsWithResourceAtLeast(resourceTypeName, minQuantity);
    }

    @Override
    public Map<Long, Integer> findResourceAmounts(String resourceTypeName, Collection<Long> classroomIds) {
        if (classroomIds == null || classroomIds.isEmpty()) {
            return Map.of();
        }
        return classroomResourceRepository.findByClassroomIdInAndDeletedAtIsNull(classroomIds).stream()
                .filter(cr -> cr.getResourceType().isActive())
                .filter(cr -> cr.getResourceType().getName().equalsIgnoreCase(resourceTypeName))
                .filter(cr -> cr.getQuantity() != null)
                .collect(Collectors.toMap(cr -> cr.getClassroom().getId(), ClassroomResource::getQuantity,
                        Integer::sum));
    }

    @Override
    public Map<Long, ClassroomSubjectPermissionDto> findSubjectPermissions(Collection<Long> classroomIds) {
        if (classroomIds == null || classroomIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, List<ClassroomPermission>> subjectsByClassroom =
                classroomPermissionRepository.findByClassroomIdInAndDeletedAtIsNull(classroomIds).stream()
                        .filter(p -> p.getTargetKind() == PermissionTargetKind.SUBJECT)
                        .collect(Collectors.groupingBy(p -> p.getClassroom().getId()));

        return classroomRepository.findAllById(classroomIds).stream()
                .collect(Collectors.toMap(Classroom::getId, classroom -> {
                    PermissionMode mode = classroom.getPermissionMode();
                    Set<Long> allowed = mode == PermissionMode.SUBSET
                            ? subjectsByClassroom.getOrDefault(classroom.getId(), List.of()).stream()
                                    .map(ClassroomPermission::getTargetId)
                                    .collect(Collectors.toUnmodifiableSet())
                            : Set.of();
                    return new ClassroomSubjectPermissionDto(classroom.getId(), mode == PermissionMode.ALL, allowed);
                }));
    }

    @Override
    public Page<ClassroomListItemDto> findAll(ClassroomFilter filter, Pageable pageable, boolean includeDeactivated) {
        log.debug("Listando aulas: filter={}, page={}, size={}, includeDeactivated={}",
                filter, pageable.getPageNumber(), pageable.getPageSize(), includeDeactivated);
        Specification<Classroom> spec = ClassroomSpecification.withFilter(filter)
                .and(includeDeactivated ? Specification.<Classroom>unrestricted() : ClassroomSpecification.available());
        Pageable byRelevance = FuzzySearch.relevanceUnlessSorted(pageable, filter.q(),
                Sort.by(Sort.Direction.ASC, "id"));
        Pageable effective = byRelevance.getSort().isSorted() ? ClassroomListSort.apply(pageable) : byRelevance;
        return classroomListComposer.compose(scopedClassroom.findAll(spec, Permission.CLASSROOM_READ, effective));
    }

    @Override
    @Transactional
    @AuditOperation("Modificación de aula")
    public ClassroomResponseDto update(Long id, ClassroomRequestDto dto) {
        log.debug("Actualizando aula: id={}, roomNumber={}", id, dto.roomNumber());

        Classroom entity = scopedClassroom.requireActiveInScope(id, Permission.CLASSROOM_UPDATE);
        Building building = findActiveBuilding(dto.buildingId());
        if (!entity.getBuilding().getId().equals(dto.buildingId())) {
            buildingScopeResolver.requireAccess(Permission.CLASSROOM_UPDATE, dto.buildingId());
        }
        ClassroomType classroomType = classroomTypeService.findById(dto.classroomTypeId());

        requireRoomNumberFree(dto.roomNumber(), building, entity.getId());
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
    @AuditOperation("Modificación de detalles de aula")
    public ClassroomListItemDto updateDetails(Long id, ClassroomDetailsUpdateDto dto) {
        log.debug("Actualizando campos locales del aula: id={}, permissionMode={}", id, dto.permissionMode());

        PermissionMode mode = normalizePermissionMode(dto.permissionMode(), dto.permissionTargets());

        Classroom classroom = scopedClassroom.requireActiveInScope(id, Permission.CLASSROOM_UPDATE);
        classroom.setClassroomType(classroomTypeService.findById(dto.classroomTypeId()));
        classroom.setObservations(dto.observations());
        classroom.setPermissionMode(mode);
        classroomRepository.save(classroom);

        classroomFeatureWriter.applyResources(classroom, dto.resources());
        classroomFeatureWriter.applyPermissions(classroom, mode, dto.permissionTargets());

        log.info("Campos locales del aula actualizados: id={}", id);
        return classroomListComposer.compose(classroom);
    }

    @Override
    @Transactional
    @AuditOperation("Baja de aula")
    public void delete(Long id) {
        log.debug("Eliminando (soft-delete) aula: id={}", id);
        Classroom classroom = scopedClassroom.requireActiveInScope(id, Permission.CLASSROOM_DELETE);
        classroomRepository.softDelete(classroom);
        log.info("Aula eliminada: id={}", id);
    }

    @Override
    @Transactional
    @AuditOperation("Reactivación de aula")
    public void activate(Long id) {
        Classroom classroom = scopedClassroom.requireAnyInScope(id, Permission.CLASSROOM_ACTIVATE);
        requireRoomNumberFree(classroom.getRoomNumber(), classroom.getBuilding(), classroom.getId());
        classroomRepository.restore(classroom);
    }

    /** Rejects the operation if another active classroom already uses (building, roomNumber). */
    private void requireRoomNumberFree(Integer roomNumber, Building building, Long excludeId) {
        boolean taken = classroomRepository.findByRoomNumberAndBuildingAndDeletedAtIsNull(roomNumber, building)
                .filter(other -> !other.getId().equals(excludeId))
                .isPresent();
        if (taken) {
            log.warn("Operación sobre aula rechazada: roomNumber '{}' ya existe en el edificio {}", roomNumber, building.getId());
            throw new SpaceDomainException("Ya existe un aula con el número " + roomNumber + " en el edificio.");
        }
    }

    @Override
    @Transactional
    @AuditOperation("Baja de aula")
    public void deactivate(Long id) {
        Classroom classroom = scopedClassroom.requireAnyInScope(id, Permission.CLASSROOM_ACTIVATE);
        classroomRepository.softDelete(classroom);
    }

    @Override
    public ClassroomResponseDto findByRoomNumberAndBuilding(Integer roomNumber, Long buildingId) {
        Building building = findActiveBuilding(buildingId);
        Optional<Classroom> inBuilding = classroomRepository.findByRoomNumberAndBuildingAndDeletedAtIsNull(roomNumber, building);
        // Un aula que existe en el edificio pero no está disponible no cae al fallback: asignaría el aula de otro edificio.
        Optional<Classroom> found = inBuilding.isPresent()
                ? inBuilding.filter(Classroom::isAvailable)
                : fallbackByRoomNumberOnly(roomNumber, buildingId);
        return classroomMapper.toDto(found.orElseThrow(() -> ResourceNotFoundException.of("Classroom", roomNumber)));
    }

    @Override
    public Optional<ClassroomResponseDto> findByRoomNumberAndBuildingCode(Integer roomNumber, Integer buildingCode) {
        return buildingRepository.findByBuildingCode(buildingCode)
                .filter(Building::isAvailable)
                .flatMap(building -> classroomRepository.findByRoomNumberAndBuildingAndDeletedAtIsNull(roomNumber, building))
                .filter(Classroom::isAvailable)
                .map(classroomMapper::toDto);
    }

    private Optional<Classroom> fallbackByRoomNumberOnly(Integer roomNumber, Long buildingId) {
        List<Classroom> matches = classroomRepository.findAllByRoomNumberAndDeletedAtIsNull(roomNumber).stream()
                .filter(Classroom::isAvailable)
                .toList();
        if (matches.size() != 1) {
            return Optional.empty();
        }
        Classroom found = matches.getFirst();
        log.warn("Aula '{}' no está en el edificio informado (buildingId={}); se usa la única "
                + "coincidencia por número, en buildingId={}", roomNumber, buildingId, found.getBuilding().getId());
        return Optional.of(found);
    }

    private Building findActiveBuilding(Long id) {
        return buildingRepository.findActiveById(id)
                .orElseThrow(() -> {
                    log.warn("Edificio no encontrado: id={}", id);
                    return ResourceNotFoundException.of("Building", id);
                });
    }

    private static PermissionMode normalizePermissionMode(PermissionMode mode,
                                                         List<ClassroomPermissionTargetRequestDto> targets) {
        boolean emptySubset = mode == PermissionMode.SUBSET && (targets == null || targets.isEmpty());
        return emptySubset ? PermissionMode.NONE : mode;
    }

    private void validateCapacity(ClassroomRequestDto dto) {
        if (dto.capacity() <= 0) {
            log.warn("Validación de capacidad fallida: capacity={}", dto.capacity());
            throw new SpaceDomainException("Capacity must be positive");
        }
    }

    @Override
    @Transactional
    @AuditOperation("Sincronización de aulas desde SysAcad")
    public int syncClassrooms(List<ClassroomSyncCommand> commands, int minValidPercent) {
        Instant syncedAt = Instant.now();
        ClassroomType defaultType = null;
        Map<Integer, Building> buildingsByCode = buildingRepository.findAll().stream()
                .filter(building -> building.getBuildingCode() != null)
                .collect(Collectors.toMap(Building::getBuildingCode, Function.identity()));
        // Agrupa por clave: V43 permite una fila borrada y una activa con el mismo (edificio, numero)
        Map<ClassroomKey, List<Classroom>> existing = classroomRepository.findAll().stream()
                .collect(Collectors.groupingBy(ClassroomServiceImpl::keyOf));
        long currentRows = existing.values().stream().flatMap(List::stream)
                .filter(classroom -> classroom.getSysacadHash() != null
                        && Boolean.TRUE.equals(classroom.getSysacadCurrent())).count();
        Set<ClassroomKey> incoming = new HashSet<>();
        Set<ClassroomKey> listed = new HashSet<>();
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
            if (command.capacity() == null || command.capacity() < 0) {
                // Listed by Sysacad with bad data: not written and not valid, but not absent either.
                listed.add(key);
                log.warn("Aula de SysAcad ignorada por capacidad ausente o negativa: aula={}, edificio={}, capacidad={}",
                        command.roomNumber(), command.buildingCode(), command.capacity());
                continue;
            }
            incoming.add(key);
            listed.add(key);
            String hash = Hashes.sha256Hex(command.capacity(), command.enabled());
            List<Classroom> rows = existing.get(key);

            if (rows == null) {
                if (defaultType == null) {
                    defaultType = resolveDefaultClassroomType();
                }
                Classroom saved = classroomRepository.save(Classroom.builder()
                        .roomNumber(key.roomNumber())
                        .building(building)
                        .classroomType(defaultType)
                        .capacity(command.capacity())
                        .sysacadEnabled(command.enabled())
                        .sysacadCurrent(true)
                        .syncedAt(syncedAt)
                        .sysacadHash(hash)
                        .build());
                existing.put(key, List.of(saved));
                affected++;
                continue;
            }
            for (Classroom classroom : rows) {
                if (isUpToDate(classroom, hash, command.enabled())) {
                    continue;
                }
                applySysacadFields(classroom, command, hash, syncedAt);
                classroomRepository.save(classroom);
                affected++;
            }
        }

        SyncGuard.requireEnoughValidRows("aulas", incoming.size(), currentRows, minValidPercent);
        return affected + markAbsent(existing.values().stream().flatMap(List::stream).toList(), listed, syncedAt);
    }

    private int markAbsent(Iterable<Classroom> existing, Set<ClassroomKey> incoming, Instant syncedAt) {
        int affected = 0;
        for (Classroom classroom : existing) {
            if (classroom.getSysacadHash() == null || incoming.contains(keyOf(classroom))
                    || Boolean.FALSE.equals(classroom.getSysacadCurrent())) {
                continue;
            }
            classroom.setSysacadCurrent(false);
            classroom.setSyncedAt(syncedAt);
            classroomRepository.save(classroom);
            affected++;
            log.info("Aula marcada como no vigente en SysAcad: id={}", classroom.getId());
        }
        return affected;
    }

    private ClassroomType resolveDefaultClassroomType() {
        return classroomTypeRepository.findByDescriptionIgnoreCaseAndDeletedAtIsNull(DEFAULT_CLASSROOM_TYPE)
                .orElseThrow(() -> new IllegalStateException(
                        "Falta el tipo de aula por defecto '" + DEFAULT_CLASSROOM_TYPE
                                + "': hay que cargarlo en la base de este ambiente"
                                + " (data.sql solo corre en dev-local)"));
    }

    private static ClassroomKey keyOf(Classroom classroom) {
        return new ClassroomKey(classroom.getBuilding().getId(), classroom.getRoomNumber());
    }

    private static boolean isUpToDate(Classroom classroom, String hash, boolean enabled) {
        return hash.equals(classroom.getSysacadHash()) && Boolean.TRUE.equals(classroom.getSysacadCurrent())
                && Boolean.valueOf(enabled).equals(classroom.getSysacadEnabled());
    }

    private record ClassroomKey(Long buildingId, Integer roomNumber) {}

    private void applySysacadFields(Classroom classroom, ClassroomSyncCommand command, String hash, Instant syncedAt) {
        classroom.setCapacity(command.capacity());
        classroom.setSysacadEnabled(command.enabled());
        classroom.setSysacadCurrent(true);
        classroom.setSyncedAt(syncedAt);
        classroom.setSysacadHash(hash);
    }
}
