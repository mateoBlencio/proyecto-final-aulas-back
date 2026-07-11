package ar.edu.utn.frc.siga.space.service.impl;

import ar.edu.utn.frc.siga.common.dto.FindOrCreateResult;
import ar.edu.utn.frc.siga.space.SpaceTestData;
import ar.edu.utn.frc.siga.space.dto.response.BuildingResponseDto;
import ar.edu.utn.frc.siga.space.mapper.BuildingMapper;
import ar.edu.utn.frc.siga.space.model.Building;
import ar.edu.utn.frc.siga.space.repository.BuildingRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
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
    @DisplayName("findOrCreate: si el edificio ya existe por nombre, no lo crea y created queda en false")
    void findOrCreateWithExistingBuildingDoesNotSave() {
        Building existing = SpaceTestData.building().name("Edificio Central").build();
        BuildingResponseDto dto = new BuildingResponseDto(1, "Edificio Central", 5, true);
        when(buildingRepository.findByName("Edificio Central")).thenReturn(Optional.of(existing));
        when(buildingMapper.toDto(existing)).thenReturn(dto);

        FindOrCreateResult<BuildingResponseDto> result = service.findOrCreate("Edificio Central");

        assertThat(result.created()).isFalse();
        assertThat(result.value()).isEqualTo(dto);
        verify(buildingRepository, never()).save(any());
    }

    @Test
    @DisplayName("findOrCreate: si no existe, crea el edificio con floorCount=0 provisional y created queda en true")
    void findOrCreateWithoutExistingBuildingCreatesProvisional() {
        BuildingResponseDto dto = new BuildingResponseDto(9, "Edificio Nuevo", 0, true);
        when(buildingRepository.findByName("Edificio Nuevo")).thenReturn(Optional.empty());
        when(buildingRepository.save(any())).thenAnswer(invocation -> {
            Building toSave = invocation.getArgument(0);
            toSave.setId(9);
            return toSave;
        });
        when(buildingMapper.toDto(any())).thenReturn(dto);

        FindOrCreateResult<BuildingResponseDto> result = service.findOrCreate("Edificio Nuevo");

        assertThat(result.created()).isTrue();
        assertThat(result.value()).isEqualTo(dto);
        ArgumentCaptor<Building> captor = ArgumentCaptor.forClass(Building.class);
        verify(buildingRepository).save(captor.capture());
        Building saved = captor.getValue();
        assertThat(saved.getName()).isEqualTo("Edificio Nuevo");
        assertThat(saved.getFloorCount()).isZero();
    }
}
