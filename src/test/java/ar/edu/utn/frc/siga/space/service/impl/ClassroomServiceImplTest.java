package ar.edu.utn.frc.siga.space.service.impl;

import ar.edu.utn.frc.siga.common.dto.FindOrCreateResult;
import ar.edu.utn.frc.siga.common.exception.ResourceNotFoundException;
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
import ar.edu.utn.frc.siga.space.service.ClassroomTypeService;
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

    @Mock
    private ClassroomRepository classroomRepository;
    @Mock
    private BuildingRepository buildingRepository;
    @Mock
    private ClassroomTypeService classroomTypeService;
    @Mock
    private ClassroomMapper classroomMapper;

    private ClassroomServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new ClassroomServiceImpl(classroomRepository, buildingRepository, classroomTypeService, classroomMapper);
    }

    // ---- create ----

    @Test
    @DisplayName("create: si roomNumber ya existe, lanza SpaceDomainException y no guarda")
    void createWithDuplicateRoomNumberThrows() {
        Building building = SpaceTestData.building().build();
        ClassroomType type = SpaceTestData.classroomType().build();
        ClassroomRequestDto dto = SpaceTestData.classroomRequestDto();
        when(buildingRepository.findById(1)).thenReturn(Optional.of(building));
        when(classroomTypeService.findById(1)).thenReturn(type);
        when(classroomRepository.findByRoomNumber("101")).thenReturn(Optional.of(SpaceTestData.classroom().build()));

        assertThatThrownBy(() -> service.create(dto))
                .isInstanceOf(SpaceDomainException.class)
                .hasMessageContaining("101");
        verify(classroomRepository, never()).save(any());
    }

    @Test
    @DisplayName("create: si floor supera building.floorCount, lanza SpaceDomainException")
    void createWithFloorAboveBuildingFloorCountThrows() {
        Building building = SpaceTestData.building().floorCount(3).build();
        ClassroomType type = SpaceTestData.classroomType().build();
        ClassroomRequestDto dto = SpaceTestData.classroomRequestDto("101", 40, 4, 1, true, 1);
        when(buildingRepository.findById(1)).thenReturn(Optional.of(building));
        when(classroomTypeService.findById(1)).thenReturn(type);
        when(classroomRepository.findByRoomNumber("101")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.create(dto))
                .isInstanceOf(SpaceDomainException.class)
                .hasMessageContaining("4")
                .hasMessageContaining("3");
        verify(classroomRepository, never()).save(any());
    }

    @Test
    @DisplayName("create: floor == building.floorCount está permitido (caso borde)")
    void createWithFloorEqualToBuildingFloorCountIsAllowed() {
        Building building = SpaceTestData.building().floorCount(3).build();
        ClassroomType type = SpaceTestData.classroomType().build();
        ClassroomRequestDto dto = SpaceTestData.classroomRequestDto("101", 40, 3, 1, true, 1);
        Classroom saved = SpaceTestData.classroom().floor(3).build();
        ClassroomResponseDto responseDto = new ClassroomResponseDto(1, "101", 3, 40, true, 1, "Edificio Central", 1, "Normal");
        when(buildingRepository.findById(1)).thenReturn(Optional.of(building));
        when(classroomTypeService.findById(1)).thenReturn(type);
        when(classroomRepository.findByRoomNumber("101")).thenReturn(Optional.empty());
        when(classroomMapper.toEntity(dto)).thenReturn(SpaceTestData.classroom().floor(3).build());
        when(classroomRepository.save(any())).thenReturn(saved);
        when(classroomMapper.toDto(saved)).thenReturn(responseDto);

        ClassroomResponseDto result = service.create(dto);

        assertThat(result).isEqualTo(responseDto);
    }

    @Test
    @DisplayName("create: capacity <= 0 lanza SpaceDomainException")
    void createWithNonPositiveCapacityThrows() {
        Building building = SpaceTestData.building().build();
        ClassroomType type = SpaceTestData.classroomType().build();
        ClassroomRequestDto dto = SpaceTestData.classroomRequestDto("101", 0, 1, 1, true, 1);
        when(buildingRepository.findById(1)).thenReturn(Optional.of(building));
        when(classroomTypeService.findById(1)).thenReturn(type);
        when(classroomRepository.findByRoomNumber("101")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.create(dto))
                .isInstanceOf(SpaceDomainException.class)
                .hasMessageContaining("Capacity");
        verify(classroomRepository, never()).save(any());
    }

    @Test
    @DisplayName("create: si el edificio está inactivo, lanza ResourceNotFoundException")
    void createWithInactiveBuildingThrowsResourceNotFound() {
        Building inactive = SpaceTestData.building().active(false).build();
        ClassroomRequestDto dto = SpaceTestData.classroomRequestDto();
        when(buildingRepository.findById(1)).thenReturn(Optional.of(inactive));

        assertThatThrownBy(() -> service.create(dto))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Building not found with id: 1");
        verify(classroomTypeService, never()).findById(any());
    }

    @Test
    @DisplayName("create: si el edificio no existe, lanza ResourceNotFoundException")
    void createWithMissingBuildingThrowsResourceNotFound() {
        ClassroomRequestDto dto = SpaceTestData.classroomRequestDto();
        when(buildingRepository.findById(1)).thenReturn(Optional.empty());

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
        Classroom mapped = Classroom.builder().roomNumber("101").floor(1).capacity(40).available(true).build();
        Classroom saved = SpaceTestData.classroom().build();
        ClassroomResponseDto responseDto = new ClassroomResponseDto(1, "101", 1, 40, true, 1, "Edificio Central", 1, "Normal");
        when(buildingRepository.findById(1)).thenReturn(Optional.of(building));
        when(classroomTypeService.findById(1)).thenReturn(type);
        when(classroomRepository.findByRoomNumber("101")).thenReturn(Optional.empty());
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

    // ---- findById ----

    @Test
    @DisplayName("findById: devuelve el DTO mapeado cuando el aula existe")
    void findByIdReturnsMappedDto() {
        Classroom classroom = SpaceTestData.classroom().build();
        ClassroomResponseDto dto = new ClassroomResponseDto(1, "101", 1, 40, true, 1, "Edificio Central", 1, "Normal");
        when(classroomRepository.findById(1)).thenReturn(Optional.of(classroom));
        when(classroomMapper.toDto(classroom)).thenReturn(dto);

        assertThat(service.findById(1)).isEqualTo(dto);
    }

    @Test
    @DisplayName("findById: si el aula no existe, lanza ResourceNotFoundException")
    void findByIdWithMissingClassroomThrowsResourceNotFound() {
        when(classroomRepository.findById(99)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.findById(99))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Classroom not found with id: 99");
    }

    // ---- findAllAvailable / findByIds / findAll ----

    @Test
    @DisplayName("findAllAvailable: mapea solo las aulas disponibles devueltas por el repositorio")
    void findAllAvailableMapsRepositoryResult() {
        Classroom classroom = SpaceTestData.classroom().build();
        ClassroomResponseDto dto = new ClassroomResponseDto(1, "101", 1, 40, true, 1, "Edificio Central", 1, "Normal");
        when(classroomRepository.findByAvailableTrue()).thenReturn(List.of(classroom));
        when(classroomMapper.toDto(classroom)).thenReturn(dto);

        assertThat(service.findAllAvailable()).containsExactly(dto);
    }

    @Test
    @DisplayName("findByIds: mapea las aulas encontradas, incluso eliminadas (no filtra)")
    void findByIdsMapsAllFound() {
        Classroom classroom = SpaceTestData.classroom().build();
        ClassroomResponseDto dto = new ClassroomResponseDto(1, "101", 1, 40, true, 1, "Edificio Central", 1, "Normal");
        when(classroomRepository.findAllById(List.of(1, 99))).thenReturn(List.of(classroom));
        when(classroomMapper.toDto(classroom)).thenReturn(dto);

        assertThat(service.findByIds(List.of(1, 99))).containsExactly(dto);
    }

    @Test
    @DisplayName("findAll: aplica el filtro vía specification y mapea la página resultante")
    void findAllMapsPagedResult() {
        ClassroomFilter filter = new ClassroomFilter("101", null, null, null, null, null, null);
        Pageable pageable = PageRequest.of(0, 10);
        Classroom classroom = SpaceTestData.classroom().build();
        ClassroomResponseDto dto = new ClassroomResponseDto(1, "101", 1, 40, true, 1, "Edificio Central", 1, "Normal");
        Page<Classroom> page = new PageImpl<>(List.of(classroom), pageable, 1);
        when(classroomRepository.findAll(ArgumentMatchers.<Specification<Classroom>>any(), eq(pageable)))
                .thenReturn(page);
        when(classroomMapper.toDto(classroom)).thenReturn(dto);

        Page<ClassroomResponseDto> result = service.findAll(filter, pageable);

        assertThat(result.getContent()).containsExactly(dto);
    }

    // ---- update ----

    @Test
    @DisplayName("update: si roomNumber pasa a coincidir con otra aula, no valida duplicado (solo create lo hace)")
    void updateDoesNotValidateDuplicateRoomNumber() {
        Classroom existing = SpaceTestData.classroom().build();
        Building building = SpaceTestData.building().build();
        ClassroomType type = SpaceTestData.classroomType().build();
        ClassroomRequestDto dto = SpaceTestData.classroomRequestDto();
        Classroom saved = SpaceTestData.classroom().build();
        ClassroomResponseDto responseDto = new ClassroomResponseDto(1, "101", 1, 40, true, 1, "Edificio Central", 1, "Normal");
        when(classroomRepository.findById(1)).thenReturn(Optional.of(existing));
        when(buildingRepository.findById(1)).thenReturn(Optional.of(building));
        when(classroomTypeService.findById(1)).thenReturn(type);
        when(classroomRepository.save(existing)).thenReturn(saved);
        when(classroomMapper.toDto(saved)).thenReturn(responseDto);

        ClassroomResponseDto result = service.update(1, dto);

        assertThat(result).isEqualTo(responseDto);
        verify(classroomRepository, never()).findByRoomNumber(any());
    }

    @Test
    @DisplayName("update: si floor supera building.floorCount, lanza SpaceDomainException")
    void updateWithFloorAboveBuildingFloorCountThrows() {
        Classroom existing = SpaceTestData.classroom().build();
        Building building = SpaceTestData.building().floorCount(2).build();
        ClassroomType type = SpaceTestData.classroomType().build();
        ClassroomRequestDto dto = SpaceTestData.classroomRequestDto("101", 40, 5, 1, true, 1);
        when(classroomRepository.findById(1)).thenReturn(Optional.of(existing));
        when(buildingRepository.findById(1)).thenReturn(Optional.of(building));
        when(classroomTypeService.findById(1)).thenReturn(type);

        assertThatThrownBy(() -> service.update(1, dto))
                .isInstanceOf(SpaceDomainException.class);
        verify(classroomRepository, never()).save(any());
    }

    @Test
    @DisplayName("update: capacity <= 0 lanza SpaceDomainException")
    void updateWithNonPositiveCapacityThrows() {
        Classroom existing = SpaceTestData.classroom().build();
        Building building = SpaceTestData.building().build();
        ClassroomType type = SpaceTestData.classroomType().build();
        ClassroomRequestDto dto = SpaceTestData.classroomRequestDto("101", -5, 1, 1, true, 1);
        when(classroomRepository.findById(1)).thenReturn(Optional.of(existing));
        when(buildingRepository.findById(1)).thenReturn(Optional.of(building));
        when(classroomTypeService.findById(1)).thenReturn(type);

        assertThatThrownBy(() -> service.update(1, dto))
                .isInstanceOf(SpaceDomainException.class);
        verify(classroomRepository, never()).save(any());
    }

    @Test
    @DisplayName("update: si el aula no existe, lanza ResourceNotFoundException")
    void updateWithMissingClassroomThrowsResourceNotFound() {
        ClassroomRequestDto dto = SpaceTestData.classroomRequestDto();
        when(classroomRepository.findById(1)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.update(1, dto))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Classroom not found with id: 1");
    }

    @Test
    @DisplayName("update: si el edificio está inactivo, lanza ResourceNotFoundException")
    void updateWithInactiveBuildingThrowsResourceNotFound() {
        Classroom existing = SpaceTestData.classroom().build();
        Building inactive = SpaceTestData.building().active(false).build();
        ClassroomRequestDto dto = SpaceTestData.classroomRequestDto();
        when(classroomRepository.findById(1)).thenReturn(Optional.of(existing));
        when(buildingRepository.findById(1)).thenReturn(Optional.of(inactive));

        assertThatThrownBy(() -> service.update(1, dto))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Building not found with id: 1");
    }

    // ---- delete ----

    @Test
    @DisplayName("delete: marca el aula como eliminada (soft-delete) sin borrado físico")
    void deleteSoftDeletesClassroom() {
        Classroom existing = SpaceTestData.classroom().deleted(false).build();
        when(classroomRepository.findById(1)).thenReturn(Optional.of(existing));

        service.delete(1);

        ArgumentCaptor<Classroom> captor = ArgumentCaptor.forClass(Classroom.class);
        verify(classroomRepository).save(captor.capture());
        assertThat(captor.getValue().getDeleted()).isTrue();
    }

    @Test
    @DisplayName("delete: si el aula no existe, lanza ResourceNotFoundException")
    void deleteWithMissingClassroomThrowsResourceNotFound() {
        when(classroomRepository.findById(1)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.delete(1))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Classroom not found with id: 1");
    }

    // ---- findOrCreate ----

    @Test
    @DisplayName("findOrCreate: si el aula ya existe en el edificio, no la crea y created queda en false")
    void findOrCreateWithExistingClassroomDoesNotSave() {
        Building building = SpaceTestData.building().build();
        Classroom existing = SpaceTestData.classroom().build();
        ClassroomResponseDto dto = new ClassroomResponseDto(1, "101", 1, 40, true, 1, "Edificio Central", 1, "Normal");
        when(buildingRepository.findById(1)).thenReturn(Optional.of(building));
        when(classroomRepository.findByRoomNumberAndBuilding("101", building)).thenReturn(Optional.of(existing));
        when(classroomMapper.toDto(existing)).thenReturn(dto);

        FindOrCreateResult<ClassroomResponseDto> result = service.findOrCreate("101", 1, 35);

        assertThat(result.created()).isFalse();
        assertThat(result.value()).isEqualTo(dto);
        verify(classroomRepository, never()).save(any());
    }

    @Test
    @DisplayName("findOrCreate: si no existe, crea con floor=0, capacity=enrolledCount y el tipo por defecto")
    void findOrCreateWithoutExistingClassroomCreatesProvisional() {
        Building building = SpaceTestData.building().build();
        ClassroomType defaultType = SpaceTestData.classroomType().build();
        when(buildingRepository.findById(1)).thenReturn(Optional.of(building));
        when(classroomRepository.findByRoomNumberAndBuilding("101", building)).thenReturn(Optional.empty());
        when(classroomTypeService.findDefault()).thenReturn(defaultType);
        when(classroomRepository.save(any())).thenAnswer(invocation -> {
            Classroom toSave = invocation.getArgument(0);
            toSave.setId(9);
            return toSave;
        });
        when(classroomMapper.toDto(any())).thenReturn(
                new ClassroomResponseDto(9, "101", 0, 35, null, 1, "Edificio Central", 1, "Normal"));

        FindOrCreateResult<ClassroomResponseDto> result = service.findOrCreate("101", 1, 35);

        assertThat(result.created()).isTrue();
        ArgumentCaptor<Classroom> captor = ArgumentCaptor.forClass(Classroom.class);
        verify(classroomRepository).save(captor.capture());
        Classroom saved = captor.getValue();
        assertThat(saved.getFloor()).isZero();
        assertThat(saved.getCapacity()).isEqualTo(35);
        assertThat(saved.getBuilding()).isEqualTo(building);
        assertThat(saved.getClassroomType()).isEqualTo(defaultType);
    }

    @Test
    @DisplayName("findOrCreate: si enrolledCount es null, la capacidad provisional queda en 1")
    void findOrCreateWithNullEnrolledCountUsesCapacityOne() {
        Building building = SpaceTestData.building().build();
        ClassroomType defaultType = SpaceTestData.classroomType().build();
        when(buildingRepository.findById(1)).thenReturn(Optional.of(building));
        when(classroomRepository.findByRoomNumberAndBuilding("101", building)).thenReturn(Optional.empty());
        when(classroomTypeService.findDefault()).thenReturn(defaultType);
        when(classroomRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(classroomMapper.toDto(any())).thenReturn(
                new ClassroomResponseDto(1, "101", 0, 1, null, 1, "Edificio Central", 1, "Normal"));

        service.findOrCreate("101", 1, null);

        ArgumentCaptor<Classroom> captor = ArgumentCaptor.forClass(Classroom.class);
        verify(classroomRepository).save(captor.capture());
        assertThat(captor.getValue().getCapacity()).isEqualTo(1);
    }

    @Test
    @DisplayName("findOrCreate: si enrolledCount es 0, la capacidad provisional queda en 1")
    void findOrCreateWithZeroEnrolledCountUsesCapacityOne() {
        Building building = SpaceTestData.building().build();
        ClassroomType defaultType = SpaceTestData.classroomType().build();
        when(buildingRepository.findById(1)).thenReturn(Optional.of(building));
        when(classroomRepository.findByRoomNumberAndBuilding("101", building)).thenReturn(Optional.empty());
        when(classroomTypeService.findDefault()).thenReturn(defaultType);
        when(classroomRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(classroomMapper.toDto(any())).thenReturn(
                new ClassroomResponseDto(1, "101", 0, 1, null, 1, "Edificio Central", 1, "Normal"));

        service.findOrCreate("101", 1, 0);

        ArgumentCaptor<Classroom> captor = ArgumentCaptor.forClass(Classroom.class);
        verify(classroomRepository).save(captor.capture());
        assertThat(captor.getValue().getCapacity()).isEqualTo(1);
    }

    @Test
    @DisplayName("findOrCreate: no exige edificio activo (usa requireBuilding, no requireActiveBuilding)")
    void findOrCreateDoesNotRequireActiveBuilding() {
        Building inactive = SpaceTestData.building().active(false).build();
        ClassroomType defaultType = SpaceTestData.classroomType().build();
        when(buildingRepository.findById(1)).thenReturn(Optional.of(inactive));
        when(classroomRepository.findByRoomNumberAndBuilding("101", inactive)).thenReturn(Optional.empty());
        when(classroomTypeService.findDefault()).thenReturn(defaultType);
        when(classroomRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(classroomMapper.toDto(any())).thenReturn(
                new ClassroomResponseDto(1, "101", 0, 1, null, 1, "Edificio Central", 1, "Normal"));

        FindOrCreateResult<ClassroomResponseDto> result = service.findOrCreate("101", 1, null);

        assertThat(result.created()).isTrue();
    }

    @Test
    @DisplayName("findOrCreate: si el edificio no existe, lanza ResourceNotFoundException")
    void findOrCreateWithMissingBuildingThrowsResourceNotFound() {
        when(buildingRepository.findById(1)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.findOrCreate("101", 1, 35))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Building not found with id: 1");
    }
}
