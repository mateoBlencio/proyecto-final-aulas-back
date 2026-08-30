package ar.edu.utn.frc.siga.space.service.impl;

import ar.edu.utn.frc.siga.common.exception.ResourceNotFoundException;
import ar.edu.utn.frc.siga.common.util.Hashes;
import ar.edu.utn.frc.siga.space.SpaceTestData;
import ar.edu.utn.frc.siga.space.dto.ClassroomFilter;
import ar.edu.utn.frc.siga.space.dto.request.ClassroomRequestDto;
import ar.edu.utn.frc.siga.space.dto.response.ClassroomResponseDto;
import ar.edu.utn.frc.siga.space.exception.SpaceDomainException;
import ar.edu.utn.frc.siga.space.mapper.ClassroomMapper;
import ar.edu.utn.frc.siga.space.model.Building;
import ar.edu.utn.frc.siga.space.model.Classroom;
import ar.edu.utn.frc.siga.space.model.ClassroomType;
import ar.edu.utn.frc.siga.space.repository.BuildingRepository;
import ar.edu.utn.frc.siga.space.repository.ClassroomRepository;
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

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
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

    private ClassroomServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new ClassroomServiceImpl(
                classroomRepository, buildingRepository, classroomTypeService, classroomTypeRepository, classroomMapper);
    }


    @Test
    @DisplayName("create: si roomNumber ya existe, lanza SpaceDomainException y no guarda")
    void createWithDuplicateRoomNumberThrows() {
        Building building = SpaceTestData.building().build();
        ClassroomType type = SpaceTestData.classroomType().build();
        ClassroomRequestDto dto = SpaceTestData.classroomRequestDto();
        when(buildingRepository.findActiveById(1L)).thenReturn(Optional.of(building));
        when(classroomTypeService.findById(1L)).thenReturn(type);
        when(classroomRepository.findByRoomNumberAndDeletedAtIsNull(101)).thenReturn(Optional.of(SpaceTestData.classroom().build()));

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
        when(classroomRepository.findByRoomNumberAndDeletedAtIsNull(101)).thenReturn(Optional.empty());

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
        ClassroomResponseDto responseDto = new ClassroomResponseDto(1L, 101, 40, 1L, "Edificio Central", 1L, "Normal");
        when(buildingRepository.findActiveById(1L)).thenReturn(Optional.of(building));
        when(classroomTypeService.findById(1L)).thenReturn(type);
        when(classroomRepository.findByRoomNumberAndDeletedAtIsNull(101)).thenReturn(Optional.empty());
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
    @DisplayName("findById: devuelve el DTO mapeado cuando el aula existe")
    void findByIdReturnsMappedDto() {
        Classroom classroom = SpaceTestData.classroom().build();
        ClassroomResponseDto dto = new ClassroomResponseDto(1L, 101, 40, 1L, "Edificio Central", 1L, "Normal");
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
        ClassroomResponseDto dto = new ClassroomResponseDto(1L, 101, 40, 1L, "Edificio Central", 1L, "Normal");
        when(classroomRepository.findAllActive()).thenReturn(List.of(classroom));
        when(classroomMapper.toDto(classroom)).thenReturn(dto);

        assertThat(service.findAllAvailable()).containsExactly(dto);
    }

    @Test
    @DisplayName("findByIds: mapea las aulas encontradas, incluso eliminadas (no filtra)")
    void findByIdsMapsAllFound() {
        Classroom classroom = SpaceTestData.classroom().build();
        ClassroomResponseDto dto = new ClassroomResponseDto(1L, 101, 40, 1L, "Edificio Central", 1L, "Normal");
        when(classroomRepository.findAllById(List.of(1L, 99L))).thenReturn(List.of(classroom));
        when(classroomMapper.toDto(classroom)).thenReturn(dto);

        assertThat(service.findByIds(List.of(1L, 99L))).containsExactly(dto);
    }

    @Test
    @DisplayName("findAll: aplica el filtro vía specification y mapea la página resultante")
    void findAllMapsPagedResult() {
        ClassroomFilter filter = new ClassroomFilter(101, null, null, null, null);
        Pageable pageable = PageRequest.of(0, 10);
        Classroom classroom = SpaceTestData.classroom().build();
        ClassroomResponseDto dto = new ClassroomResponseDto(1L, 101, 40, 1L, "Edificio Central", 1L, "Normal");
        Page<Classroom> page = new PageImpl<>(List.of(classroom), pageable, 1);
        when(classroomRepository.findAll(ArgumentMatchers.<Specification<Classroom>>any(), eq(pageable)))
                .thenReturn(page);
        when(classroomMapper.toDto(classroom)).thenReturn(dto);

        Page<ClassroomResponseDto> result = service.findAll(filter, pageable);

        assertThat(result.getContent()).containsExactly(dto);
    }


    @Test
    @DisplayName("update: si roomNumber pasa a coincidir con otra aula, no valida duplicado (solo create lo hace)")
    void updateDoesNotValidateDuplicateRoomNumber() {
        Classroom existing = SpaceTestData.classroom().build();
        Building building = SpaceTestData.building().build();
        ClassroomType type = SpaceTestData.classroomType().build();
        ClassroomRequestDto dto = SpaceTestData.classroomRequestDto();
        Classroom saved = SpaceTestData.classroom().build();
        ClassroomResponseDto responseDto = new ClassroomResponseDto(1L, 101, 40, 1L, "Edificio Central", 1L, "Normal");
        when(classroomRepository.findActiveById(1L)).thenReturn(Optional.of(existing));
        when(buildingRepository.findActiveById(1L)).thenReturn(Optional.of(building));
        when(classroomTypeService.findById(1L)).thenReturn(type);
        when(classroomRepository.save(existing)).thenReturn(saved);
        when(classroomMapper.toDto(saved)).thenReturn(responseDto);

        ClassroomResponseDto result = service.update(1L, dto);

        assertThat(result).isEqualTo(responseDto);
        verify(classroomRepository, never()).findByRoomNumberAndDeletedAtIsNull(any());
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
    @DisplayName("findByRoomNumberAndBuilding: si el aula existe en el edificio, devuelve el DTO mapeado")
    void findByRoomNumberAndBuildingReturnsMappedDto() {
        Building building = SpaceTestData.building().build();
        Classroom existing = SpaceTestData.classroom().build();
        ClassroomResponseDto dto = new ClassroomResponseDto(1L, 101, 40, 1L, "Edificio Central", 1L, "Normal");
        when(buildingRepository.findById(1L)).thenReturn(Optional.of(building));
        when(classroomRepository.findByRoomNumberAndBuildingAndDeletedAtIsNull(101, building)).thenReturn(Optional.of(existing));
        when(classroomMapper.toDto(existing)).thenReturn(dto);

        ClassroomResponseDto result = service.findByRoomNumberAndBuilding(101, 1L);

        assertThat(result).isEqualTo(dto);
        verify(classroomRepository, never()).save(any());
    }

    @Test
    @DisplayName("findByRoomNumberAndBuilding: no exige edificio activo")
    void findByRoomNumberAndBuildingDoesNotRequireActiveBuilding() {
        Building inactive = SpaceTestData.deactivated(SpaceTestData.building().build());
        Classroom existing = SpaceTestData.classroom().build();
        ClassroomResponseDto dto = new ClassroomResponseDto(1L, 101, 40, 1L, "Edificio Central", 1L, "Normal");
        when(buildingRepository.findById(1L)).thenReturn(Optional.of(inactive));
        when(classroomRepository.findByRoomNumberAndBuildingAndDeletedAtIsNull(101, inactive)).thenReturn(Optional.of(existing));
        when(classroomMapper.toDto(existing)).thenReturn(dto);

        ClassroomResponseDto result = service.findByRoomNumberAndBuilding(101, 1L);

        assertThat(result).isEqualTo(dto);
    }

    @Test
    @DisplayName("findByRoomNumberAndBuilding: si no está en el edificio informado, hace fallback a buscar solo por número")
    void findByRoomNumberAndBuildingFallsBackToRoomNumberOnly() {
        Building informedBuilding = SpaceTestData.building().id(2L).name("Edif. Ing.Inchaurrondo").build();
        Classroom actual = SpaceTestData.classroom().build();
        ClassroomResponseDto dto = new ClassroomResponseDto(1L, 101, 40, 1L, "Edificio Central", 1L, "Normal");
        when(buildingRepository.findById(2L)).thenReturn(Optional.of(informedBuilding));
        when(classroomRepository.findByRoomNumberAndBuildingAndDeletedAtIsNull(101, informedBuilding)).thenReturn(Optional.empty());
        when(classroomRepository.findAllByRoomNumberAndDeletedAtIsNull(101)).thenReturn(List.of(actual));
        when(classroomMapper.toDto(actual)).thenReturn(dto);

        ClassroomResponseDto result = service.findByRoomNumberAndBuilding(101, 2L);

        assertThat(result).isEqualTo(dto);
    }

    @Test
    @DisplayName("findByRoomNumberAndBuilding: si el número es ambiguo (más de un aula en catálogo), no adivina y lanza ResourceNotFoundException")
    void findByRoomNumberAndBuildingWithAmbiguousRoomNumberThrowsResourceNotFound() {
        Building informedBuilding = SpaceTestData.building().id(2L).name("Otro edificio").build();
        Classroom other1 = SpaceTestData.classroom().id(10L).build();
        Classroom other2 = SpaceTestData.classroom().id(11L).build();
        when(buildingRepository.findById(2L)).thenReturn(Optional.of(informedBuilding));
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
        when(buildingRepository.findById(1L)).thenReturn(Optional.of(building));
        when(classroomRepository.findByRoomNumberAndBuildingAndDeletedAtIsNull(101, building)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.findByRoomNumberAndBuilding(101, 1L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Classroom not found with id: 101");
    }

    @Test
    @DisplayName("findByRoomNumberAndBuilding: si el edificio no existe, lanza ResourceNotFoundException")
    void findByRoomNumberAndBuildingWithMissingBuildingThrowsResourceNotFound() {
        when(buildingRepository.findById(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.findByRoomNumberAndBuilding(101, 1L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Building not found with id: 1");
    }

    @Test
    @DisplayName("findByRoomNumberAndBuildingCode: aula y edificio existen por código de SysAcad -> devuelve el DTO mapeado")
    void findByRoomNumberAndBuildingCodeReturnsMappedDto() {
        Building building = SpaceTestData.building().buildingCode(28).build();
        Classroom existing = SpaceTestData.classroom().build();
        ClassroomResponseDto dto = new ClassroomResponseDto(1L, 101, 40, 1L, "Edificio Central", 1L, "Normal");
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
        ClassroomType defaultType = SpaceTestData.classroomType().description("Normal").build();
        when(buildingRepository.findAll()).thenReturn(List.of(SYNC_BUILDING));
        when(classroomRepository.findAll()).thenReturn(List.of());
        when(classroomTypeRepository.findByDescriptionIgnoreCaseAndDeletedAtIsNull("Normal")).thenReturn(Optional.of(defaultType));
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
        when(classroomTypeRepository.findByDescriptionIgnoreCaseAndDeletedAtIsNull("Normal")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.syncClassrooms(List.of(new ClassroomSyncCommand(101, 2, true, 70))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Normal");
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
