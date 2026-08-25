package ar.edu.utn.frc.siga.space.service.impl;

import ar.edu.utn.frc.siga.common.exception.ResourceNotFoundException;
import ar.edu.utn.frc.siga.space.SpaceTestData;
import ar.edu.utn.frc.siga.space.config.SpaceSettings;
import ar.edu.utn.frc.siga.space.dto.request.BuildingActiveBatchItemDto;
import ar.edu.utn.frc.siga.space.dto.response.BuildingResponseDto;
import ar.edu.utn.frc.siga.space.mapper.BuildingMapper;
import ar.edu.utn.frc.siga.space.model.Building;
import ar.edu.utn.frc.siga.space.repository.BuildingRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("BuildingServiceImpl")
class BuildingServiceImplTest {

    @Mock
    private BuildingRepository buildingRepository;
    @Mock
    private BuildingMapper buildingMapper;
    @Mock
    private SpaceSettings spaceSettings;

    private BuildingServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new BuildingServiceImpl(buildingRepository, buildingMapper, spaceSettings);
    }

    @Test
    @DisplayName("findAll: con el filtro prendido y sin includeInactive, devuelve solo los edificios activos")
    void findAllReturnsOnlyActiveBuildingsMapped() {
        Building active = SpaceTestData.building().id(1L).build();
        Building inactive = SpaceTestData.building().id(2L).deletedAt(Instant.now()).build();
        BuildingResponseDto dto = new BuildingResponseDto(1L, "Edificio Central", true);
        when(spaceSettings.isFilterInactiveBuildings()).thenReturn(true);
        when(buildingRepository.findAll()).thenReturn(List.of(active, inactive));
        when(buildingMapper.toDto(active)).thenReturn(dto);

        List<BuildingResponseDto> result = service.findAll(false);

        assertThat(result).containsExactly(dto);
    }

    @Test
    @DisplayName("findAll: con el filtro apagado por setting, devuelve todos los edificios")
    void findAllReturnsAllBuildingsWhenFilterDisabled() {
        Building active = SpaceTestData.building().id(1L).build();
        Building inactive = SpaceTestData.building().id(2L).deletedAt(Instant.now()).build();
        BuildingResponseDto activeDto = new BuildingResponseDto(1L, "Edificio Central", true);
        BuildingResponseDto inactiveDto = new BuildingResponseDto(2L, "Edificio Anexo", false);
        when(spaceSettings.isFilterInactiveBuildings()).thenReturn(false);
        when(buildingRepository.findAll()).thenReturn(List.of(active, inactive));
        when(buildingMapper.toDto(active)).thenReturn(activeDto);
        when(buildingMapper.toDto(inactive)).thenReturn(inactiveDto);

        List<BuildingResponseDto> result = service.findAll(false);

        assertThat(result).containsExactly(activeDto, inactiveDto);
    }

    @Test
    @DisplayName("findAll: con includeInactive=true, devuelve todos los edificios sin consultar el setting")
    void findAllReturnsAllBuildingsWhenIncludeInactiveRequested() {
        Building active = SpaceTestData.building().id(1L).build();
        Building inactive = SpaceTestData.building().id(2L).deletedAt(Instant.now()).build();
        BuildingResponseDto activeDto = new BuildingResponseDto(1L, "Edificio Central", true);
        BuildingResponseDto inactiveDto = new BuildingResponseDto(2L, "Edificio Anexo", false);
        when(buildingRepository.findAll()).thenReturn(List.of(active, inactive));
        when(buildingMapper.toDto(active)).thenReturn(activeDto);
        when(buildingMapper.toDto(inactive)).thenReturn(inactiveDto);

        List<BuildingResponseDto> result = service.findAll(true);

        assertThat(result).containsExactly(activeDto, inactiveDto);
        verify(spaceSettings, org.mockito.Mockito.never()).isFilterInactiveBuildings();
    }

    @Test
    @DisplayName("findById: devuelve el DTO mapeado cuando el edificio existe")
    void findByIdReturnsMappedDto() {
        Building existing = SpaceTestData.building().id(1L).build();
        BuildingResponseDto dto = new BuildingResponseDto(1L, "Edificio Central", true);
        when(buildingRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(buildingMapper.toDto(existing)).thenReturn(dto);

        assertThat(service.findById(1L)).isEqualTo(dto);
    }

    @Test
    @DisplayName("findById: si el edificio no existe, lanza ResourceNotFoundException")
    void findByIdWithMissingBuildingThrowsResourceNotFound() {
        when(buildingRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.findById(99L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Building not found with id: 99");
    }

    @Test
    @DisplayName("findByName: devuelve el DTO mapeado cuando el edificio existe")
    void findByNameReturnsMappedDto() {
        Building existing = SpaceTestData.building().name("Edificio Central").build();
        BuildingResponseDto dto = new BuildingResponseDto(1L, "Edificio Central", true);
        when(buildingRepository.findByName("Edificio Central")).thenReturn(Optional.of(existing));
        when(buildingMapper.toDto(existing)).thenReturn(dto);

        assertThat(service.findByName("Edificio Central")).isEqualTo(dto);
    }

    @Test
    @DisplayName("findByName: si el edificio no existe, lanza ResourceNotFoundException")
    void findByNameWithMissingBuildingThrowsResourceNotFound() {
        when(buildingRepository.findByName("Edificio Nuevo")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.findByName("Edificio Nuevo"))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Building not found with id: Edificio Nuevo");
    }

    @Test
    @DisplayName("setActive: desactiva el edificio marcando deletedAt")
    void setActiveDeactivatesBuilding() {
        Building building = SpaceTestData.building().id(1L).build();
        BuildingResponseDto dto = new BuildingResponseDto(1L, "Edificio Central", false);
        when(buildingRepository.findById(1L)).thenReturn(Optional.of(building));
        when(buildingRepository.save(building)).thenReturn(building);
        when(buildingMapper.toDto(building)).thenReturn(dto);

        BuildingResponseDto result = service.setActive(1L, false);

        assertThat(building.getDeletedAt()).isNotNull();
        assertThat(result).isEqualTo(dto);
    }

    @Test
    @DisplayName("setActive: si el edificio no existe, lanza ResourceNotFoundException")
    void setActiveWithMissingBuildingThrowsResourceNotFound() {
        when(buildingRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.setActive(99L, true))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Building not found with id: 99");
    }

    @Test
    @DisplayName("setActiveBatch: aplica el cambio a cada edificio del lote")
    void setActiveBatchAppliesEachItem() {
        Building building1 = SpaceTestData.building().id(1L).build();
        Building building2 = SpaceTestData.building().id(2L).build();
        BuildingResponseDto dto1 = new BuildingResponseDto(1L, "Edificio Central", false);
        BuildingResponseDto dto2 = new BuildingResponseDto(2L, "Edificio Anexo", false);
        when(buildingRepository.findById(1L)).thenReturn(Optional.of(building1));
        when(buildingRepository.findById(2L)).thenReturn(Optional.of(building2));
        when(buildingRepository.save(building1)).thenReturn(building1);
        when(buildingRepository.save(building2)).thenReturn(building2);
        when(buildingMapper.toDto(building1)).thenReturn(dto1);
        when(buildingMapper.toDto(building2)).thenReturn(dto2);

        List<BuildingResponseDto> result = service.setActiveBatch(List.of(
                new BuildingActiveBatchItemDto(1L, false),
                new BuildingActiveBatchItemDto(2L, false)));

        assertThat(result).containsExactly(dto1, dto2);
        assertThat(building1.getDeletedAt()).isNotNull();
        assertThat(building2.getDeletedAt()).isNotNull();
    }

    @Test
    @DisplayName("setActiveBatch: si un edificio del lote no existe, lanza ResourceNotFoundException")
    void setActiveBatchWithMissingBuildingThrowsResourceNotFound() {
        when(buildingRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.setActiveBatch(List.of(new BuildingActiveBatchItemDto(99L, true))))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Building not found with id: 99");
    }
}
