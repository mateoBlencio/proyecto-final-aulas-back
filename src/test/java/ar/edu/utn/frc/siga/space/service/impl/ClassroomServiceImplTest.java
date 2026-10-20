package ar.edu.utn.frc.siga.space.service.impl;

import ar.edu.utn.frc.siga.common.exception.ResourceNotFoundException;
import ar.edu.utn.frc.siga.common.security.BuildingScope;
import ar.edu.utn.frc.siga.common.security.BuildingScopeResolver;
import ar.edu.utn.frc.siga.common.security.Permission;
import ar.edu.utn.frc.siga.common.util.Hashes;
import ar.edu.utn.frc.siga.space.SpaceTestData;
import ar.edu.utn.frc.siga.space.dto.ClassroomFilter;
import ar.edu.utn.frc.siga.space.dto.request.ClassroomRequestDto;
import ar.edu.utn.frc.siga.space.dto.response.ClassroomListItemDto;
import ar.edu.utn.frc.siga.space.dto.response.ClassroomResponseDto;
import ar.edu.utn.frc.siga.space.exception.SpaceDomainException;
import ar.edu.utn.frc.siga.space.mapper.ClassroomListComposer;
import ar.edu.utn.frc.siga.space.mapper.ClassroomMapper;
import ar.edu.utn.frc.siga.space.dto.response.ClassroomSubjectPermissionDto;
import ar.edu.utn.frc.siga.space.model.Building;
import ar.edu.utn.frc.siga.space.model.Classroom;
import ar.edu.utn.frc.siga.space.model.ClassroomPermission;
import ar.edu.utn.frc.siga.space.model.ClassroomResource;
import ar.edu.utn.frc.siga.space.model.ClassroomType;
import ar.edu.utn.frc.siga.space.model.ResourceType;
import ar.edu.utn.frc.siga.space.model.PermissionMode;
import ar.edu.utn.frc.siga.space.model.PermissionTargetKind;
import ar.edu.utn.frc.siga.space.repository.BuildingRepository;
import ar.edu.utn.frc.siga.space.repository.ClassroomPermissionRepository;
import ar.edu.utn.frc.siga.space.repository.ClassroomRepository;
import ar.edu.utn.frc.siga.space.repository.ClassroomResourceRepository;
import ar.edu.utn.frc.siga.space.repository.ClassroomTypeRepository;
import ar.edu.utn.frc.siga.space.service.ClassroomTypeService;
import ar.edu.utn.frc.siga.space.service.command.ClassroomSyncCommand;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.AccessDeniedException;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static ar.edu.utn.frc.siga.space.service.ClassroomService.DEFAULT_CLASSROOM_TYPE;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("ClassroomServiceImpl")
class ClassroomServiceImplTest {

    private static final Building SYNC_BUILDING = SpaceTestData.building().id(1L).buildingCode(2).build();

    @Mock
    private ClassroomRepository classroomRepository;
    @Mock
    private BuildingRepository buildingRepository;
    @Mock
    private ClassroomTypeService classroomTypeService;
    @Mock
    private ClassroomTypeRepository classroomTypeRepository;
    @Mock
    private ClassroomMapper classroomMapper;
    @Mock
    private ClassroomListComposer classroomListComposer;
    @Mock
    private ClassroomFeatureWriter classroomFeatureWriter;
    @Mock
    private BuildingScopeResolver buildingScopeResolver;
    @Mock
    private ClassroomPermissionRepository classroomPermissionRepository;
    @Mock
    private ClassroomResourceRepository classroomResourceRepository;

    private ClassroomServiceImpl service;

    @BeforeEach
    void setUp() {
        ScopedClassroomFinder scopedClassroom =
                new ScopedClassroomFinder(classroomRepository, buildingScopeResolver);
        service = new ClassroomServiceImpl(
                classroomRepository, buildingRepository, classroomTypeService, classroomTypeRepository,
                classroomMapper, classroomListComposer, classroomFeatureWriter, buildingScopeResolver,
                scopedClassroom, classroomPermissionRepository, classroomResourceRepository);
        lenient().when(buildingScopeResolver.scopeFor(any())).thenReturn(BuildingScope.unrestricted());
    }


    @Test
    @DisplayName("create: si roomNumber ya existe, lanza SpaceDomainException y no guarda")
    void createWithDuplicateRoomNumberThrows() {
        Building building = SpaceTestData.building().build();
        ClassroomType type = SpaceTestData.classroomType().build();
        ClassroomRequestDto dto = SpaceTestData.classroomRequestDto();
        when(buildingRepository.findActiveById(1L)).thenReturn(Optional.of(building));
        when(classroomTypeService.findById(1L)).thenReturn(type);
        when(classroomRepository.findByRoomNumberAndBuildingAndDeletedAtIsNull(101, building)).thenReturn(Optional.of(SpaceTestData.classroom().build()));

        assertThatThrownBy(() -> service.create(dto))
                .isInstanceOf(SpaceDomainException.class)
                .hasMessageContaining("101");
        verify(classroomRepository, never()).save(any());
    }

    @Test
    @DisplayName("create: capacity <= 0 lanza SpaceDomainException")
    void createWithNonPositiveCapacityThrows() {
        Building building = SpaceTestData.building().build();
        ClassroomType type = SpaceTestData.classroomType().build();
        ClassroomRequestDto dto = SpaceTestData.classroomRequestDto(101, 0, 1L, 1L);
        when(buildingRepository.findActiveById(1L)).thenReturn(Optional.of(building));
        when(classroomTypeService.findById(1L)).thenReturn(type);
        when(classroomRepository.findByRoomNumberAndBuildingAndDeletedAtIsNull(101, building)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.create(dto))
                .isInstanceOf(SpaceDomainException.class)
                .hasMessageContaining("Capacity");
        verify(classroomRepository, never()).save(any());
    }

    @Test
    @DisplayName("create: si el edificio está inactivo, lanza ResourceNotFoundException")
    void createWithInactiveBuildingThrowsResourceNotFound() {
        ClassroomRequestDto dto = SpaceTestData.classroomRequestDto();
        when(buildingRepository.findActiveById(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.create(dto))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Building not found with id: 1");
        verify(classroomTypeService, never()).findById(any());
    }

    @Test
    @DisplayName("create: si el edificio no existe, lanza ResourceNotFoundException")
    void createWithMissingBuildingThrowsResourceNotFound() {
        ClassroomRequestDto dto = SpaceTestData.classroomRequestDto();
        when(buildingRepository.findActiveById(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.create(dto))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Building not found with id: 1");
    }

    @Test
    @DisplayName("create: happy path guarda el aula con edificio y tipo resueltos")
    void createHappyPathSavesClassroomWithResolvedBuildingAndType() {
        Building building = SpaceTestData.building().build();
        ClassroomType type = SpaceTestData.classroomType().build();
        ClassroomRequestDto dto = SpaceTestData.classroomRequestDto();
        Classroom mapped = Classroom.builder().roomNumber(101).capacity(40).build();
        Classroom saved = SpaceTestData.classroom().build();
        ClassroomResponseDto responseDto = new ClassroomResponseDto(1L, 101, 40, 1L, "Edificio Central", 1L, "Normal", null, true);
        when(buildingRepository.findActiveById(1L)).thenReturn(Optional.of(building));
        when(classroomTypeService.findById(1L)).thenReturn(type);
        when(classroomRepository.findByRoomNumberAndBuildingAndDeletedAtIsNull(101, building)).thenReturn(Optional.empty());
        when(classroomMapper.toEntity(dto)).thenReturn(mapped);
        when(classroomRepository.save(mapped)).thenReturn(saved);
        when(classroomMapper.toDto(saved)).thenReturn(responseDto);

        ClassroomResponseDto result = service.create(dto);

        assertThat(result).isEqualTo(responseDto);
        ArgumentCaptor<Classroom> captor = ArgumentCaptor.forClass(Classroom.class);
        verify(classroomRepository).save(captor.capture());
        Classroom toSave = captor.getValue();
        assertThat(toSave.getBuilding()).isEqualTo(building);
        assertThat(toSave.getClassroomType()).isEqualTo(type);
    }

    @Test
    @DisplayName("create: el mismo número en otro edificio no bloquea la creación")
    void createWithRoomNumberUsedInAnotherBuildingDoesNotThrow() {
        Building building = SpaceTestData.building().build();
        ClassroomType type = SpaceTestData.classroomType().build();
        ClassroomRequestDto dto = SpaceTestData.classroomRequestDto();
        Classroom mapped = Classroom.builder().roomNumber(101).capacity(40).build();
        Classroom saved = SpaceTestData.classroom().build();
        when(buildingRepository.findActiveById(1L)).thenReturn(Optional.of(building));
        when(classroomTypeService.findById(1L)).thenReturn(type);
        when(classroomRepository.findByRoomNumberAndBuildingAndDeletedAtIsNull(101, building)).thenReturn(Optional.empty());
        when(classroomMapper.toEntity(dto)).thenReturn(mapped);
        when(classroomRepository.save(mapped)).thenReturn(saved);

        service.create(dto);

        verify(classroomRepository).save(mapped);
    }

    @Test
    @DisplayName("create: sin alcance sobre el edificio → AccessDeniedException, no guarda")
    void createWithoutBuildingScopeThrowsAccessDenied() {
        Building building = SpaceTestData.building().build();
        ClassroomRequestDto dto = SpaceTestData.classroomRequestDto();
        when(buildingRepository.findActiveById(1L)).thenReturn(Optional.of(building));
        doThrow(new AccessDeniedException("sin acceso"))
                .when(buildingScopeResolver).requireAccess(Permission.CLASSROOM_CREATE, 1L);

        assertThatThrownBy(() -> service.create(dto)).isInstanceOf(AccessDeniedException.class);

        verify(classroomRepository, never()).save(any());
    }


    @Test
    @DisplayName("findById: devuelve el DTO mapeado cuando el aula existe")
    void findByIdReturnsMappedDto() {
        Classroom classroom = SpaceTestData.classroom().build();
        ClassroomResponseDto dto = new ClassroomResponseDto(1L, 101, 40, 1L, "Edificio Central", 1L, "Normal", null, true);
        when(classroomRepository.findActiveById(1L)).thenReturn(Optional.of(classroom));
        when(classroomMapper.toDto(classroom)).thenReturn(dto);

        assertThat(service.findById(1L)).isEqualTo(dto);
    }

    @Test
    @DisplayName("findById: si el aula no existe, lanza ResourceNotFoundException")
    void findByIdWithMissingClassroomThrowsResourceNotFound() {
        when(classroomRepository.findActiveById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.findById(99L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Classroom not found with id: 99");
    }


    @Test
    @DisplayName("findAllAvailable: mapea las aulas activas devueltas por el repositorio")
    void findAllAvailableMapsRepositoryResult() {
        Classroom classroom = SpaceTestData.classroom().build();
        ClassroomResponseDto dto = new ClassroomResponseDto(1L, 101, 40, 1L, "Edificio Central", 1L, "Normal", null, true);
        when(classroomRepository.findAll(ArgumentMatchers.<Specification<Classroom>>any()))
                .thenReturn(List.of(classroom));
        when(classroomMapper.toDto(classroom)).thenReturn(dto);

        assertThat(service.findAllAvailable()).containsExactly(dto);
    }

    @Test
    @DisplayName("findByIds: mapea las aulas encontradas, incluso eliminadas (no filtra)")
    void findByIdsMapsAllFound() {
        Classroom classroom = SpaceTestData.classroom().build();
        ClassroomResponseDto dto = new ClassroomResponseDto(1L, 101, 40, 1L, "Edificio Central", 1L, "Normal", null, true);
        when(classroomRepository.findAllById(List.of(1L, 99L))).thenReturn(List.of(classroom));
        when(classroomMapper.toDto(classroom)).thenReturn(dto);

        assertThat(service.findByIds(List.of(1L, 99L))).containsExactly(dto);
    }

    @Test
    @DisplayName("findByIds excluye las aulas desactivadas y findByIdsIncludingDeactivated las incluye")
    void findByIds_excludesDeactivated_butIncludingDeactivatedKeepsThem() {
        Classroom active = SpaceTestData.classroom().id(1L).build();
        Classroom deactivated = SpaceTestData.classroom().id(2L).build();
        deactivated.deactivate();
        ClassroomResponseDto activeDto = new ClassroomResponseDto(1L, 101, 40, 1L, "Central", 1L, "Normal", null, true);
        ClassroomResponseDto deactivatedDto = new ClassroomResponseDto(2L, 102, 40, 1L, "Central", 1L, "Normal", null, true);
        when(classroomRepository.findAllById(List.of(1L, 2L))).thenReturn(List.of(active, deactivated));
        when(classroomMapper.toDto(active)).thenReturn(activeDto);
        lenient().when(classroomMapper.toDto(deactivated)).thenReturn(deactivatedDto);

        assertThat(service.findByIds(List.of(1L, 2L))).containsExactly(activeDto);
        assertThat(service.findByIdsIncludingDeactivated(List.of(1L, 2L))).containsExactly(activeDto, deactivatedDto);
    }

    @Test
    @DisplayName("findAll: aplica filtro vía specification y delega el armado del listado en el composer")
    void findAllMapsPagedResult() {
        ClassroomFilter filter = new ClassroomFilter(101, null, null, null, null, false);
        Pageable pageable = PageRequest.of(0, 10);
        Classroom classroom = SpaceTestData.classroom().build();
        Page<Classroom> page = new PageImpl<>(List.of(classroom), pageable, 1);
        ClassroomListItemDto item = new ClassroomListItemDto(1L, 101, 1L, "Edificio Central", 1L, "Normal", 40,
                List.of(), null, true, null, "Todas", List.of(), null, true);
        Page<ClassroomListItemDto> composed = new PageImpl<>(List.of(item), pageable, 1);
        when(classroomRepository.findAll(ArgumentMatchers.<Specification<Classroom>>any(), any(Pageable.class)))
                .thenReturn(page);
        when(classroomListComposer.compose(page)).thenReturn(composed);

        Page<ClassroomListItemDto> result = service.findAll(filter, pageable, false);

        assertThat(result.getContent()).containsExactly(item);
    }


    @Test
    @DisplayName("update: si el número lo usa otra aula activa del mismo edificio, lanza y no guarda")
    void updateWithRoomNumberUsedByAnotherClassroomThrows() {
        Classroom existing = SpaceTestData.classroom().id(1L).build();
        Classroom other = SpaceTestData.classroom().id(2L).roomNumber(102).build();
        Building building = SpaceTestData.building().build();
        ClassroomRequestDto dto = SpaceTestData.classroomRequestDto(102, 40, 1L, 1L);
        when(classroomRepository.findActiveById(1L)).thenReturn(Optional.of(existing));
        when(buildingRepository.findActiveById(1L)).thenReturn(Optional.of(building));
        when(classroomTypeService.findById(1L)).thenReturn(SpaceTestData.classroomType().build());
        when(classroomRepository.findByRoomNumberAndBuildingAndDeletedAtIsNull(102, building))
                .thenReturn(Optional.of(other));

        assertThatThrownBy(() -> service.update(1L, dto))
                .isInstanceOf(SpaceDomainException.class)
                .hasMessage("Ya existe un aula con el número 102 en el edificio.");
        verify(classroomRepository, never()).save(any());
    }

    @Test
    @DisplayName("update: si cambia de edificio y el número choca en el destino, lanza y no guarda")
    void updateMovingToBuildingWhereRoomNumberIsTakenThrows() {
        Classroom existing = SpaceTestData.classroom().id(1L).build();
        Building target = SpaceTestData.building().id(2L).build();
        Classroom other = SpaceTestData.classroom().id(7L).building(target).build();
        ClassroomRequestDto dto = SpaceTestData.classroomRequestDto(101, 40, 1L, 2L);
        when(classroomRepository.findActiveById(1L)).thenReturn(Optional.of(existing));
        when(buildingRepository.findActiveById(2L)).thenReturn(Optional.of(target));
        when(classroomTypeService.findById(1L)).thenReturn(SpaceTestData.classroomType().build());
        when(classroomRepository.findByRoomNumberAndBuildingAndDeletedAtIsNull(101, target))
                .thenReturn(Optional.of(other));

        assertThatThrownBy(() -> service.update(1L, dto)).isInstanceOf(SpaceDomainException.class);
        verify(classroomRepository, never()).save(any());
    }

    @Test
    @DisplayName("update: conservar su propio número y edificio (el repo la devuelve a ella misma) no lanza")
    void updateKeepingOwnRoomNumberDoesNotThrow() {
        Classroom existing = SpaceTestData.classroom().id(1L).build();
        Building building = SpaceTestData.building().build();
        ClassroomRequestDto dto = SpaceTestData.classroomRequestDto();
        ClassroomResponseDto responseDto = new ClassroomResponseDto(1L, 101, 40, 1L, "Edificio Central", 1L, "Normal", null, true);
        when(classroomRepository.findActiveById(1L)).thenReturn(Optional.of(existing));
        when(buildingRepository.findActiveById(1L)).thenReturn(Optional.of(building));
        when(classroomTypeService.findById(1L)).thenReturn(SpaceTestData.classroomType().build());
        when(classroomRepository.findByRoomNumberAndBuildingAndDeletedAtIsNull(101, building))
                .thenReturn(Optional.of(existing));
        when(classroomRepository.save(existing)).thenReturn(existing);
        when(classroomMapper.toDto(existing)).thenReturn(responseDto);

        assertThat(service.update(1L, dto)).isEqualTo(responseDto);
        verify(classroomRepository).save(existing);
    }

    @Test
    @DisplayName("update: con un número libre en el edificio guarda")
    void updateWithFreeRoomNumberSaves() {
        Classroom existing = SpaceTestData.classroom().id(1L).build();
        Building building = SpaceTestData.building().build();
        ClassroomRequestDto dto = SpaceTestData.classroomRequestDto(205, 40, 1L, 1L);
        ClassroomResponseDto responseDto = new ClassroomResponseDto(1L, 205, 40, 1L, "Edificio Central", 1L, "Normal", null, true);
        when(classroomRepository.findActiveById(1L)).thenReturn(Optional.of(existing));
        when(buildingRepository.findActiveById(1L)).thenReturn(Optional.of(building));
        when(classroomTypeService.findById(1L)).thenReturn(SpaceTestData.classroomType().build());
        when(classroomRepository.findByRoomNumberAndBuildingAndDeletedAtIsNull(205, building))
                .thenReturn(Optional.empty());
        when(classroomRepository.save(existing)).thenReturn(existing);
        when(classroomMapper.toDto(existing)).thenReturn(responseDto);

        assertThat(service.update(1L, dto)).isEqualTo(responseDto);
        verify(classroomRepository).save(existing);
    }

    @Test
    @DisplayName("update: capacity <= 0 lanza SpaceDomainException")
    void updateWithNonPositiveCapacityThrows() {
        Classroom existing = SpaceTestData.classroom().build();
        Building building = SpaceTestData.building().build();
        ClassroomType type = SpaceTestData.classroomType().build();
        ClassroomRequestDto dto = SpaceTestData.classroomRequestDto(101, -5, 1L, 1L);
        when(classroomRepository.findActiveById(1L)).thenReturn(Optional.of(existing));
        when(buildingRepository.findActiveById(1L)).thenReturn(Optional.of(building));
        when(classroomTypeService.findById(1L)).thenReturn(type);

        assertThatThrownBy(() -> service.update(1L, dto))
                .isInstanceOf(SpaceDomainException.class);
        verify(classroomRepository, never()).save(any());
    }

    @Test
    @DisplayName("update: si el aula no existe, lanza ResourceNotFoundException")
    void updateWithMissingClassroomThrowsResourceNotFound() {
        ClassroomRequestDto dto = SpaceTestData.classroomRequestDto();
        when(classroomRepository.findActiveById(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.update(1L, dto))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Classroom not found with id: 1");
    }

    @Test
    @DisplayName("update: si el edificio está inactivo, lanza ResourceNotFoundException")
    void updateWithInactiveBuildingThrowsResourceNotFound() {
        Classroom existing = SpaceTestData.classroom().build();
        ClassroomRequestDto dto = SpaceTestData.classroomRequestDto();
        when(classroomRepository.findActiveById(1L)).thenReturn(Optional.of(existing));
        when(buildingRepository.findActiveById(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.update(1L, dto))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Building not found with id: 1");
    }

    @Test
    @DisplayName("update: sin alcance sobre el edificio del aula → 404 (el fetch acotado no la ve), no guarda")
    void updateWithoutBuildingScopeThrowsNotFound() {
        Classroom existing = SpaceTestData.classroom().build();
        ClassroomRequestDto dto = SpaceTestData.classroomRequestDto();
        when(classroomRepository.findActiveById(1L)).thenReturn(Optional.of(existing));
        when(buildingScopeResolver.scopeFor(Permission.CLASSROOM_UPDATE))
                .thenReturn(BuildingScope.of(Set.of(2L)));

        assertThatThrownBy(() -> service.update(1L, dto)).isInstanceOf(ResourceNotFoundException.class);

        verify(classroomRepository, never()).save(any());
    }


    @Test
    @DisplayName("delete: marca el aula como eliminada (soft-delete) sin borrado físico")
    void deleteSoftDeletesClassroom() {
        Classroom existing = SpaceTestData.classroom().build();
        when(classroomRepository.findActiveById(1L)).thenReturn(Optional.of(existing));

        service.delete(1L);

        verify(classroomRepository).softDelete(existing);
    }

    @Test
    @DisplayName("delete: si el aula no existe, lanza ResourceNotFoundException")
    void deleteWithMissingClassroomThrowsResourceNotFound() {
        when(classroomRepository.findActiveById(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.delete(1L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Classroom not found with id: 1");
    }

    @Test
    @DisplayName("delete: sin alcance sobre el edificio del aula → 404 (el fetch acotado no la ve), no borra")
    void deleteWithoutBuildingScopeThrowsNotFound() {
        Classroom existing = SpaceTestData.classroom().build();
        when(classroomRepository.findActiveById(1L)).thenReturn(Optional.of(existing));
        when(buildingScopeResolver.scopeFor(Permission.CLASSROOM_DELETE))
                .thenReturn(BuildingScope.of(Set.of(2L)));

        assertThatThrownBy(() -> service.delete(1L)).isInstanceOf(ResourceNotFoundException.class);

        verify(classroomRepository, never()).softDelete(any());
    }

    @Test
    @DisplayName("activate: sin alcance sobre el edificio del aula → 404 (el fetch acotado no la ve), no reactiva")
    void activateWithoutBuildingScopeThrowsNotFound() {
        Classroom existing = SpaceTestData.classroom().build();
        when(classroomRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(buildingScopeResolver.scopeFor(Permission.CLASSROOM_ACTIVATE))
                .thenReturn(BuildingScope.of(Set.of(2L)));

        assertThatThrownBy(() -> service.activate(1L)).isInstanceOf(ResourceNotFoundException.class);

        verify(classroomRepository, never()).restore(any());
    }

    @Test
    @DisplayName("activate: si otra aula activa usa el número en el edificio, lanza y no restaura")
    void activateWithRoomNumberTakenThrows() {
        Classroom deleted = SpaceTestData.classroom().id(1L).build();
        Classroom other = SpaceTestData.classroom().id(2L).build();
        when(classroomRepository.findById(1L)).thenReturn(Optional.of(deleted));
        when(classroomRepository.findByRoomNumberAndBuildingAndDeletedAtIsNull(101, deleted.getBuilding()))
                .thenReturn(Optional.of(other));

        assertThatThrownBy(() -> service.activate(1L))
                .isInstanceOf(SpaceDomainException.class)
                .hasMessage("Ya existe un aula con el número 101 en el edificio.");
        verify(classroomRepository, never()).restore(any());
    }

    @Test
    @DisplayName("activate: sin choque de número restaura el aula")
    void activateWithFreeRoomNumberRestores() {
        Classroom deleted = SpaceTestData.classroom().id(1L).build();
        when(classroomRepository.findById(1L)).thenReturn(Optional.of(deleted));
        when(classroomRepository.findByRoomNumberAndBuildingAndDeletedAtIsNull(101, deleted.getBuilding()))
                .thenReturn(Optional.empty());

        service.activate(1L);

        verify(classroomRepository).restore(deleted);
    }

    @Test
    @DisplayName("activate: sobre un aula ya activa que el repo devuelve como ella misma no lanza")
    void activateAlreadyActiveClassroomDoesNotConflictWithItself() {
        Classroom active = SpaceTestData.classroom().id(1L).build();
        when(classroomRepository.findById(1L)).thenReturn(Optional.of(active));
        when(classroomRepository.findByRoomNumberAndBuildingAndDeletedAtIsNull(101, active.getBuilding()))
                .thenReturn(Optional.of(active));

        service.activate(1L);

        verify(classroomRepository).restore(active);
    }


    @Test
    @DisplayName("findByRoomNumberAndBuilding: si el aula existe en el edificio, devuelve el DTO mapeado")
    void findByRoomNumberAndBuildingReturnsMappedDto() {
        Building building = SpaceTestData.building().build();
        Classroom existing = SpaceTestData.classroom().build();
        ClassroomResponseDto dto = new ClassroomResponseDto(1L, 101, 40, 1L, "Edificio Central", 1L, "Normal", null, true);
        when(buildingRepository.findActiveById(1L)).thenReturn(Optional.of(building));
        when(classroomRepository.findByRoomNumberAndBuildingAndDeletedAtIsNull(101, building)).thenReturn(Optional.of(existing));
        when(classroomMapper.toDto(existing)).thenReturn(dto);

        ClassroomResponseDto result = service.findByRoomNumberAndBuilding(101, 1L);

        assertThat(result).isEqualTo(dto);
        verify(classroomRepository, never()).save(any());
    }

    @Test
    @DisplayName("findByRoomNumberAndBuilding: si el edificio está inactivo, lanza ResourceNotFoundException")
    void findByRoomNumberAndBuildingWithInactiveBuildingThrowsResourceNotFound() {
        when(buildingRepository.findActiveById(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.findByRoomNumberAndBuilding(101, 1L))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("findByRoomNumberAndBuilding: si no está en el edificio informado, hace fallback a buscar solo por número")
    void findByRoomNumberAndBuildingFallsBackToRoomNumberOnly() {
        Building informedBuilding = SpaceTestData.building().id(2L).name("Edif. Ing.Inchaurrondo").build();
        Classroom actual = SpaceTestData.classroom().build();
        ClassroomResponseDto dto = new ClassroomResponseDto(1L, 101, 40, 1L, "Edificio Central", 1L, "Normal", null, true);
        when(buildingRepository.findActiveById(2L)).thenReturn(Optional.of(informedBuilding));
        when(classroomRepository.findByRoomNumberAndBuildingAndDeletedAtIsNull(101, informedBuilding)).thenReturn(Optional.empty());
        when(classroomRepository.findAllByRoomNumberAndDeletedAtIsNull(101)).thenReturn(List.of(actual));
        when(classroomMapper.toDto(actual)).thenReturn(dto);

        ClassroomResponseDto result = service.findByRoomNumberAndBuilding(101, 2L);

        assertThat(result).isEqualTo(dto);
    }

    @Test
    @DisplayName("findByRoomNumberAndBuilding: si el aula existe en un edificio no vigente en SysAcad, no hace fallback al aula de otro edificio")
    void findByRoomNumberAndBuildingWithUnavailableClassroomDoesNotFallBack() {
        Building informedBuilding = SpaceTestData.building().id(2L).name("Edif. Ing.Inchaurrondo")
                .sysacadHash("hash").sysacadEnabled(false).build();
        Classroom inNotCurrentBuilding = SpaceTestData.classroom().building(informedBuilding).build();
        when(buildingRepository.findActiveById(2L)).thenReturn(Optional.of(informedBuilding));
        when(classroomRepository.findByRoomNumberAndBuildingAndDeletedAtIsNull(101, informedBuilding)).thenReturn(Optional.of(inNotCurrentBuilding));

        assertThatThrownBy(() -> service.findByRoomNumberAndBuilding(101, 2L))
                .isInstanceOf(ResourceNotFoundException.class);
        verify(classroomRepository, never()).findAllByRoomNumberAndDeletedAtIsNull(any());
    }

    @Test
    @DisplayName("findByRoomNumberAndBuilding: un aula no vigente en SysAcad del edificio informado se devuelve")
    void findByRoomNumberAndBuildingReturnsClassroomNotCurrentInSysacad() {
        Building informedBuilding = SpaceTestData.building().id(2L).name("Edif. Ing.Inchaurrondo").build();
        Classroom notCurrent = SpaceTestData.classroom().building(informedBuilding).sysacadHash("hash").sysacadEnabled(false).build();
        ClassroomResponseDto dto = new ClassroomResponseDto(1L, 101, 40, 2L, "Edif. Ing.Inchaurrondo", 1L, "Normal", null, false);
        when(buildingRepository.findActiveById(2L)).thenReturn(Optional.of(informedBuilding));
        when(classroomRepository.findByRoomNumberAndBuildingAndDeletedAtIsNull(101, informedBuilding)).thenReturn(Optional.of(notCurrent));
        when(classroomMapper.toDto(notCurrent)).thenReturn(dto);

        assertThat(service.findByRoomNumberAndBuilding(101, 2L)).isEqualTo(dto);
    }

    @Test
    @DisplayName("findByRoomNumberAndBuildingCode: un aula no vigente en SysAcad de un edificio vigente se devuelve")
    void findByRoomNumberAndBuildingCodeReturnsClassroomNotCurrentInSysacad() {
        Building building = SpaceTestData.building().buildingCode(7).build();
        Classroom notCurrent = SpaceTestData.classroom().building(building).sysacadHash("hash").sysacadEnabled(false).build();
        ClassroomResponseDto dto = new ClassroomResponseDto(1L, 101, 40, 1L, "Edificio Central", 1L, "Normal", null, false);
        when(buildingRepository.findByBuildingCode(7)).thenReturn(Optional.of(building));
        when(classroomRepository.findByRoomNumberAndBuildingAndDeletedAtIsNull(101, building)).thenReturn(Optional.of(notCurrent));
        when(classroomMapper.toDto(notCurrent)).thenReturn(dto);

        assertThat(service.findByRoomNumberAndBuildingCode(101, 7)).contains(dto);
    }

    @Test
    @DisplayName("findByRoomNumberAndBuilding: si el número es ambiguo (más de un aula en catálogo), no adivina y lanza ResourceNotFoundException")
    void findByRoomNumberAndBuildingWithAmbiguousRoomNumberThrowsResourceNotFound() {
        Building informedBuilding = SpaceTestData.building().id(2L).name("Otro edificio").build();
        Classroom other1 = SpaceTestData.classroom().id(10L).build();
        Classroom other2 = SpaceTestData.classroom().id(11L).build();
        when(buildingRepository.findActiveById(2L)).thenReturn(Optional.of(informedBuilding));
        when(classroomRepository.findByRoomNumberAndBuildingAndDeletedAtIsNull(999, informedBuilding)).thenReturn(Optional.empty());
        when(classroomRepository.findAllByRoomNumberAndDeletedAtIsNull(999)).thenReturn(List.of(other1, other2));

        assertThatThrownBy(() -> service.findByRoomNumberAndBuilding(999, 2L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Classroom not found with id: 999");
    }

    @Test
    @DisplayName("findByRoomNumberAndBuilding: si el aula no existe en el edificio, lanza ResourceNotFoundException")
    void findByRoomNumberAndBuildingWithMissingClassroomThrowsResourceNotFound() {
        Building building = SpaceTestData.building().build();
        when(buildingRepository.findActiveById(1L)).thenReturn(Optional.of(building));
        when(classroomRepository.findByRoomNumberAndBuildingAndDeletedAtIsNull(101, building)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.findByRoomNumberAndBuilding(101, 1L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Classroom not found with id: 101");
    }

    @Test
    @DisplayName("findByRoomNumberAndBuilding: si el edificio no existe, lanza ResourceNotFoundException")
    void findByRoomNumberAndBuildingWithMissingBuildingThrowsResourceNotFound() {
        when(buildingRepository.findActiveById(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.findByRoomNumberAndBuilding(101, 1L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Building not found with id: 1");
    }

    @Test
    @DisplayName("findByRoomNumberAndBuildingCode: aula y edificio existen por código de SysAcad -> devuelve el DTO mapeado")
    void findByRoomNumberAndBuildingCodeReturnsMappedDto() {
        Building building = SpaceTestData.building().buildingCode(28).build();
        Classroom existing = SpaceTestData.classroom().build();
        ClassroomResponseDto dto = new ClassroomResponseDto(1L, 101, 40, 1L, "Edificio Central", 1L, "Normal", null, true);
        when(buildingRepository.findByBuildingCode(28)).thenReturn(Optional.of(building));
        when(classroomRepository.findByRoomNumberAndBuildingAndDeletedAtIsNull(101, building)).thenReturn(Optional.of(existing));
        when(classroomMapper.toDto(existing)).thenReturn(dto);

        Optional<ClassroomResponseDto> result = service.findByRoomNumberAndBuildingCode(101, 28);

        assertThat(result).contains(dto);
    }

    @Test
    @DisplayName("findByRoomNumberAndBuildingCode: no existe edificio con ese código de SysAcad -> vacío, sin fallback")
    void findByRoomNumberAndBuildingCodeReturnsEmptyWhenBuildingCodeUnknown() {
        when(buildingRepository.findByBuildingCode(24)).thenReturn(Optional.empty());

        Optional<ClassroomResponseDto> result = service.findByRoomNumberAndBuildingCode(999, 24);

        assertThat(result).isEmpty();
        verify(classroomRepository, never()).findByRoomNumberAndBuildingAndDeletedAtIsNull(any(), any());
    }

    @Test
    @DisplayName("findByRoomNumberAndBuildingCode: edificio existe pero no tiene esa aula -> vacío")
    void findByRoomNumberAndBuildingCodeReturnsEmptyWhenClassroomMissing() {
        Building building = SpaceTestData.building().buildingCode(28).build();
        when(buildingRepository.findByBuildingCode(28)).thenReturn(Optional.of(building));
        when(classroomRepository.findByRoomNumberAndBuildingAndDeletedAtIsNull(101, building)).thenReturn(Optional.empty());

        Optional<ClassroomResponseDto> result = service.findByRoomNumberAndBuildingCode(101, 28);

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("syncClassrooms: inserta el aula nueva enlazada al edificio por su código de SysAcad, con tipo por defecto")
    void syncClassroomsInsertsUnknownClassroom() {
        ClassroomType defaultType = SpaceTestData.classroomType().description(DEFAULT_CLASSROOM_TYPE).build();
        when(buildingRepository.findAll()).thenReturn(List.of(SYNC_BUILDING));
        when(classroomRepository.findAll()).thenReturn(List.of());
        when(classroomTypeRepository.findByDescriptionIgnoreCaseAndDeletedAtIsNull(DEFAULT_CLASSROOM_TYPE)).thenReturn(Optional.of(defaultType));
        when(classroomRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        int affected = service.syncClassrooms(List.of(new ClassroomSyncCommand(101, 2, true, 70)));

        ArgumentCaptor<Classroom> saved = ArgumentCaptor.forClass(Classroom.class);
        verify(classroomRepository).save(saved.capture());
        Classroom inserted = saved.getValue();
        assertThat(inserted.getRoomNumber()).isEqualTo(101);
        assertThat(inserted.getBuilding()).isSameAs(SYNC_BUILDING);
        assertThat(inserted.getClassroomType()).isSameAs(defaultType);
        assertThat(inserted.getCapacity()).isEqualTo(70);
        assertThat(inserted.getSysacadEnabled()).isTrue();
        assertThat(inserted.getSysacadHash()).isEqualTo(Hashes.sha256Hex(70, true));
        assertThat(affected).isEqualTo(1);
    }

    @Test
    @DisplayName("syncClassrooms: falla con mensaje claro si falta el tipo de aula por defecto")
    void syncClassroomsFailsWhenDefaultClassroomTypeMissing() {
        when(buildingRepository.findAll()).thenReturn(List.of(SYNC_BUILDING));
        when(classroomRepository.findAll()).thenReturn(List.of());
        when(classroomTypeRepository.findByDescriptionIgnoreCaseAndDeletedAtIsNull(DEFAULT_CLASSROOM_TYPE)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.syncClassrooms(List.of(new ClassroomSyncCommand(101, 2, true, 70))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(DEFAULT_CLASSROOM_TYPE);
        verify(classroomRepository, never()).save(any());
    }

    @Test
    @DisplayName("syncClassrooms: actualiza capacidad y habilitada_sysacad sin pisar el tipo de aula local")
    void syncClassroomsUpdatesOnlySysacadOwnedFields() {
        ClassroomType type = SpaceTestData.classroomType().build();
        Classroom existing = syncClassroom(101, 40, true, Hashes.sha256Hex(40, true));
        existing.setClassroomType(type);
        when(buildingRepository.findAll()).thenReturn(List.of(SYNC_BUILDING));
        when(classroomRepository.findAll()).thenReturn(List.of(existing));

        service.syncClassrooms(List.of(new ClassroomSyncCommand(101, 2, false, 70)));

        assertThat(existing.getCapacity()).isEqualTo(70);
        assertThat(existing.getSysacadEnabled()).isFalse();
        assertThat(existing.getClassroomType()).isSameAs(type);
        verify(classroomRepository).save(existing);
    }

    @Test
    @DisplayName("syncClassrooms: no escribe cuando el hash no cambió")
    void syncClassroomsSkipsUnchangedClassroom() {
        Classroom existing = syncClassroom(101, 70, true, Hashes.sha256Hex(70, true));
        when(buildingRepository.findAll()).thenReturn(List.of(SYNC_BUILDING));
        when(classroomRepository.findAll()).thenReturn(List.of(existing));

        int affected = service.syncClassrooms(List.of(new ClassroomSyncCommand(101, 2, true, 70)));

        verify(classroomRepository, never()).save(any());
        assertThat(affected).isZero();
    }

    @Test
    @DisplayName("syncClassrooms: ignora el aula cuyo edificio no está replicado")
    void syncClassroomsSkipsClassroomWithUnknownBuilding() {
        when(buildingRepository.findAll()).thenReturn(List.of(SYNC_BUILDING));
        when(classroomRepository.findAll()).thenReturn(List.of());

        int affected = service.syncClassrooms(List.of(new ClassroomSyncCommand(101, 99, true, 70)));

        verify(classroomRepository, never()).save(any());
        assertThat(affected).isZero();
    }

    @Test
    @DisplayName("syncClassrooms: el aula ausente upstream queda no vigente en SysAcad, sin borrarse")
    void syncClassroomsMarksAbsentClassroomAsNotCurrent() {
        Classroom absent = syncClassroom(101, 70, true, Hashes.sha256Hex(70, true));
        when(buildingRepository.findAll()).thenReturn(List.of(SYNC_BUILDING));
        when(classroomRepository.findAll()).thenReturn(List.of(absent));

        service.syncClassrooms(List.of());

        assertThat(absent.getSysacadEnabled()).isFalse();
        verify(classroomRepository).save(absent);
    }

    @Test
    @DisplayName("syncClassrooms: no vuelve a guardar un aula ausente que ya estaba deshabilitada")
    void syncClassroomsSkipsAlreadyDisabledAbsentClassroom() {
        Classroom alreadyDisabled = syncClassroom(101, 70, false, Hashes.sha256Hex(70, false));
        when(buildingRepository.findAll()).thenReturn(List.of(SYNC_BUILDING));
        when(classroomRepository.findAll()).thenReturn(List.of(alreadyDisabled));

        int affected = service.syncClassrooms(List.of());

        verify(classroomRepository, never()).save(any());
        assertThat(affected).isZero();
    }

    @Test
    @DisplayName("syncClassrooms: con borrada y activa del mismo (edificio, número) actualiza ambas sin crear filas")
    void syncClassroomsUpdatesBothDeletedAndActiveRowsOfSameKey() {
        Instant old = Instant.parse("2020-01-01T00:00:00Z");
        Classroom deleted = syncClassroom(101, 40, true, Hashes.sha256Hex(40, true));
        deleted.setId(11L);
        deleted.deactivate();
        deleted.setSyncedAt(old);
        Classroom active = syncClassroom(101, 50, true, Hashes.sha256Hex(50, true));
        active.setId(12L);
        active.setSyncedAt(old);
        when(buildingRepository.findAll()).thenReturn(List.of(SYNC_BUILDING));
        when(classroomRepository.findAll()).thenReturn(List.of(deleted, active));

        int affected = service.syncClassrooms(List.of(new ClassroomSyncCommand(101, 2, true, 70)));

        assertThat(affected).isEqualTo(2);
        for (Classroom row : List.of(deleted, active)) {
            assertThat(row.getCapacity()).isEqualTo(70);
            assertThat(row.getSysacadEnabled()).isTrue();
            assertThat(row.getSysacadHash()).isEqualTo(Hashes.sha256Hex(70, true));
            assertThat(row.getSyncedAt()).isAfter(old);
        }
        assertThat(deleted.isDeleted()).isTrue();
        verify(classroomRepository).save(deleted);
        verify(classroomRepository).save(active);
        verify(classroomRepository, times(2)).save(any());
    }

    @Test
    @DisplayName("syncClassrooms: con borrada y activa, si la activa ya está al día solo actualiza la borrada")
    void syncClassroomsUpdatesOnlyStaleRowOfPair() {
        Classroom deleted = syncClassroom(101, 40, true, Hashes.sha256Hex(40, true));
        deleted.setId(13L);
        deleted.deactivate();
        Classroom active = syncClassroom(101, 70, true, Hashes.sha256Hex(70, true));
        active.setId(14L);
        when(buildingRepository.findAll()).thenReturn(List.of(SYNC_BUILDING));
        when(classroomRepository.findAll()).thenReturn(List.of(deleted, active));

        int affected = service.syncClassrooms(List.of(new ClassroomSyncCommand(101, 2, true, 70)));

        assertThat(affected).isEqualTo(1);
        assertThat(deleted.getCapacity()).isEqualTo(70);
        verify(classroomRepository).save(deleted);
        verify(classroomRepository, never()).save(same(active));
    }

    @Test
    @DisplayName("syncClassrooms: si solo existe la borrada la actualiza, no crea otra y sigue borrada")
    void syncClassroomsUpdatesLoneDeletedRowWithoutCreating() {
        Classroom deleted = syncClassroom(101, 40, true, Hashes.sha256Hex(40, true));
        deleted.setId(15L);
        deleted.deactivate();
        when(buildingRepository.findAll()).thenReturn(List.of(SYNC_BUILDING));
        when(classroomRepository.findAll()).thenReturn(List.of(deleted));

        int affected = service.syncClassrooms(List.of(new ClassroomSyncCommand(101, 2, true, 70)));

        assertThat(affected).isEqualTo(1);
        assertThat(deleted.getCapacity()).isEqualTo(70);
        assertThat(deleted.isDeleted()).isTrue();
        verify(classroomRepository).save(deleted);
        verify(classroomRepository, times(1)).save(any());
        verifyNoInteractions(classroomTypeRepository);
    }

    @Test
    @DisplayName("syncClassrooms: key ausente en SysAcad deshabilita borrada y activa; las ya deshabilitadas no cuentan")
    void syncClassroomsMarksAbsentDeletedAndActiveRows() {
        Instant old = Instant.parse("2020-01-01T00:00:00Z");
        Classroom deleted = syncClassroom(101, 70, true, Hashes.sha256Hex(70, true));
        deleted.setId(16L);
        deleted.deactivate();
        deleted.setSyncedAt(old);
        Classroom active = syncClassroom(101, 70, true, Hashes.sha256Hex(70, true));
        active.setId(17L);
        active.setSyncedAt(old);
        Classroom alreadyOff = syncClassroom(102, 70, false, Hashes.sha256Hex(70, false));
        alreadyOff.setId(18L);
        alreadyOff.deactivate();
        alreadyOff.setSyncedAt(old);
        when(buildingRepository.findAll()).thenReturn(List.of(SYNC_BUILDING));
        when(classroomRepository.findAll()).thenReturn(List.of(deleted, active, alreadyOff));

        int affected = service.syncClassrooms(List.of());

        assertThat(affected).isEqualTo(2);
        assertThat(deleted.getSysacadEnabled()).isFalse();
        assertThat(active.getSysacadEnabled()).isFalse();
        assertThat(deleted.getSyncedAt()).isAfter(old);
        assertThat(active.getSyncedAt()).isAfter(old);
        assertThat(alreadyOff.getSyncedAt()).isEqualTo(old);
        verify(classroomRepository, never()).save(same(alreadyOff));
    }

    @Test
    @DisplayName("syncClassrooms: key sin filas crea una sola")
    void syncClassroomsCreatesSingleRowForUnknownKey() {
        ClassroomType defaultType = SpaceTestData.classroomType().description(DEFAULT_CLASSROOM_TYPE).build();
        when(buildingRepository.findAll()).thenReturn(List.of(SYNC_BUILDING));
        when(classroomRepository.findAll()).thenReturn(List.of());
        when(classroomTypeRepository.findByDescriptionIgnoreCaseAndDeletedAtIsNull(DEFAULT_CLASSROOM_TYPE))
                .thenReturn(Optional.of(defaultType));
        when(classroomRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        int affected = service.syncClassrooms(List.of(new ClassroomSyncCommand(101, 2, true, 70)));

        assertThat(affected).isEqualTo(1);
        verify(classroomRepository, times(1)).save(any());
    }

    @Test
    @DisplayName("findSubjectPermissions: ALL abre a todas, SUBSET expone las materias, NONE queda vacío")
    void findSubjectPermissionsResuelveModo() {
        Classroom all = SpaceTestData.classroom().id(1L).permissionMode(PermissionMode.ALL).build();
        Classroom subset = SpaceTestData.classroom().id(2L).permissionMode(PermissionMode.SUBSET).build();
        Classroom none = SpaceTestData.classroom().id(3L).permissionMode(PermissionMode.NONE).build();
        ClassroomPermission subsetPermission = ClassroomPermission.builder()
                .classroom(subset).targetKind(PermissionTargetKind.SUBJECT).targetId(77L).build();
        when(classroomPermissionRepository.findByClassroomIdInAndDeletedAtIsNull(any()))
                .thenReturn(List.of(subsetPermission));
        when(classroomRepository.findAllById(any())).thenReturn(List.of(all, subset, none));

        Map<Long, ClassroomSubjectPermissionDto> result =
                service.findSubjectPermissions(List.of(1L, 2L, 3L));

        assertThat(result.get(1L).openToAll()).isTrue();
        assertThat(result.get(2L).openToAll()).isFalse();
        assertThat(result.get(2L).allowedSubjectIds()).containsExactly(77L);
        assertThat(result.get(2L).permits(77L)).isTrue();
        assertThat(result.get(3L).openToAll()).isFalse();
        assertThat(result.get(3L).allowedSubjectIds()).isEmpty();
        assertThat(result.get(3L).permits(77L)).isFalse();
    }

    @Test
    @DisplayName("findSubjectPermissions: lista vacía no consulta la base")
    void findSubjectPermissionsVacio() {
        assertThat(service.findSubjectPermissions(List.of())).isEmpty();
        verify(classroomRepository, never()).findAllById(any());
    }

    @Test
    @DisplayName("findResourceAmounts: suma por aula solo el recurso pedido, sin importar mayúsculas, y descarta tipos dados de baja")
    void findResourceAmountsFiltersByActiveResourceName() {
        ResourceType pcs = ResourceType.builder().id(1L).name("Cantidad de PC").build();
        ResourceType retiredPcs = ResourceType.builder().id(2L).name("Cantidad de PC").build();
        retiredPcs.deactivate(java.time.Instant.now());
        ResourceType projector = ResourceType.builder().id(3L).name("Proyector").build();
        Classroom lab1 = Classroom.builder().id(1L).build();
        Classroom lab2 = Classroom.builder().id(2L).build();
        when(classroomResourceRepository.findByClassroomIdInAndDeletedAtIsNull(List.of(1L, 2L, 3L))).thenReturn(List.of(
                ClassroomResource.builder().classroom(lab1).resourceType(pcs).quantity(35).build(),
                ClassroomResource.builder().classroom(lab1).resourceType(projector).quantity(1).build(),
                ClassroomResource.builder().classroom(lab2).resourceType(retiredPcs).quantity(99).build(),
                ClassroomResource.builder().classroom(lab2).resourceType(pcs).quantity(null).build()));

        assertThat(service.findResourceAmounts("cantidad de pc", List.of(1L, 2L, 3L))).containsExactly(Map.entry(1L, 35));
    }

    @Test
    @DisplayName("findResourceAmounts: sin ids no consulta el repositorio")
    void findResourceAmountsWithoutIdsReturnsEmpty() {
        assertThat(service.findResourceAmounts("Cantidad de PC", List.of())).isEmpty();
        verifyNoInteractions(classroomResourceRepository);
    }

    private static Classroom syncClassroom(Integer roomNumber, Integer capacity, Boolean sysacadEnabled, String hash) {
        return SpaceTestData.classroom()
                .roomNumber(roomNumber)
                .capacity(capacity)
                .sysacadEnabled(sysacadEnabled)
                .building(SYNC_BUILDING)
                .classroomType(null)
                .sysacadHash(hash)
                .build();
    }
}
