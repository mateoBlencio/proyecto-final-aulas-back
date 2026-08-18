package ar.edu.utn.frc.siga.space.service.impl;

import ar.edu.utn.frc.siga.common.exception.ResourceNotFoundException;
import ar.edu.utn.frc.siga.space.SpaceTestData;
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

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("BuildingServiceImpl")
class BuildingServiceImplTest {

    @Mock
    private BuildingRepository buildingRepository;
    @Mock
    private BuildingMapper buildingMapper;

    private BuildingServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new BuildingServiceImpl(buildingRepository, buildingMapper);
    }

    @Test
    @DisplayName("findAll: devuelve solo los edificios activos, mapeados")
    void findAllReturnsOnlyActiveBuildingsMapped() {
        Building active = SpaceTestData.building().id(1).active(true).build();
        Building inactive = SpaceTestData.building().id(2).active(false).build();
        BuildingResponseDto dto = new BuildingResponseDto(1, "Edificio Central", 5, true);
        when(buildingRepository.findAll()).thenReturn(List.of(active, inactive));
        when(buildingMapper.toDto(active)).thenReturn(dto);

        List<BuildingResponseDto> result = service.findAll();

        assertThat(result).containsExactly(dto);
    }

    @Test
    @DisplayName("findById: devuelve el DTO mapeado cuando el edificio existe")
    void findByIdReturnsMappedDto() {
        Building existing = SpaceTestData.building().id(1).build();
        BuildingResponseDto dto = new BuildingResponseDto(1, "Edificio Central", 5, true);
        when(buildingRepository.findById(1)).thenReturn(Optional.of(existing));
        when(buildingMapper.toDto(existing)).thenReturn(dto);

        assertThat(service.findById(1)).isEqualTo(dto);
    }

    @Test
    @DisplayName("findById: si el edificio no existe, lanza ResourceNotFoundException")
    void findByIdWithMissingBuildingThrowsResourceNotFound() {
        when(buildingRepository.findById(99)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.findById(99))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Building not found with id: 99");
    }

    @Test
    @DisplayName("findByName: devuelve el DTO mapeado cuando el edificio existe")
    void findByNameReturnsMappedDto() {
        Building existing = SpaceTestData.building().name("Edificio Central").build();
        BuildingResponseDto dto = new BuildingResponseDto(1, "Edificio Central", 5, true);
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
}
