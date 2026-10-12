package ar.edu.utn.frc.siga.space.service.impl;

import ar.edu.utn.frc.siga.common.exception.ResourceNotFoundException;
import ar.edu.utn.frc.siga.common.security.BuildingScope;
import ar.edu.utn.frc.siga.common.security.BuildingScopeResolver;
import ar.edu.utn.frc.siga.common.security.Permission;
import ar.edu.utn.frc.siga.common.util.Hashes;
import ar.edu.utn.frc.siga.space.SpaceTestData;
import ar.edu.utn.frc.siga.space.dto.BuildingFilter;
import ar.edu.utn.frc.siga.space.dto.request.BuildingActiveBatchItemDto;
import ar.edu.utn.frc.siga.space.dto.response.BuildingResponseDto;
import ar.edu.utn.frc.siga.space.mapper.BuildingMapper;
import ar.edu.utn.frc.siga.space.model.Building;
import ar.edu.utn.frc.siga.space.repository.BuildingRepository;
import ar.edu.utn.frc.siga.space.service.command.BuildingSyncCommand;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
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
    private BuildingScopeResolver buildingScopeResolver;

    private BuildingServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new BuildingServiceImpl(buildingRepository, buildingMapper, buildingScopeResolver);
        lenient().when(buildingScopeResolver.scopeFor(Permission.BUILDING_READ)).thenReturn(BuildingScope.unrestricted());
    }

    @Test
    @DisplayName("findAll: devuelve la página de edificios mapeados según el Specification")
    void findAllReturnsMappedPage() {
        Building active = SpaceTestData.building().id(1L).build();
        BuildingResponseDto dto = new BuildingResponseDto(1L, "Edificio Central", true, null);
        when(buildingRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(active)));
        when(buildingMapper.toDto(active)).thenReturn(dto);

        assertThat(service.findAll(new BuildingFilter(null), Pageable.unpaged(), false).getContent())
                .containsExactly(dto);
    }

    @Test
    @DisplayName("findAll: con includeDeactivated=true, no restringe por deletedAt")
    void findAllWithIncludeDeactivatedMapsEveryStatus() {
        Building active = SpaceTestData.building().id(1L).build();
        Building inactive = SpaceTestData.deactivated(SpaceTestData.building().id(2L).build());
        BuildingResponseDto activeDto = new BuildingResponseDto(1L, "Edificio Central", true, null);
        BuildingResponseDto inactiveDto = new BuildingResponseDto(2L, "Edificio Anexo", false, null);
        when(buildingRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(active, inactive)));
        when(buildingMapper.toDto(active)).thenReturn(activeDto);
        when(buildingMapper.toDto(inactive)).thenReturn(inactiveDto);

        assertThat(service.findAll(new BuildingFilter(null), Pageable.unpaged(), true).getContent())
                .containsExactly(activeDto, inactiveDto);
    }

    @Test
    @DisplayName("findAll: resuelve el alcance de BUILDING_READ para acotar el listado")
    void findAllConsultsBuildingReadScope() {
        when(buildingScopeResolver.scopeFor(Permission.BUILDING_READ)).thenReturn(BuildingScope.of(Set.of(5L)));
        when(buildingRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));

        service.findAll(new BuildingFilter(null), Pageable.unpaged(), false);

        verify(buildingScopeResolver).scopeFor(Permission.BUILDING_READ);
    }

    @Test
    @DisplayName("findById: devuelve el DTO mapeado cuando el edificio existe")
    void findByIdReturnsMappedDto() {
        Building existing = SpaceTestData.building().id(1L).build();
        BuildingResponseDto dto = new BuildingResponseDto(1L, "Edificio Central", true, null);
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
        BuildingResponseDto dto = new BuildingResponseDto(1L, "Edificio Central", true, null);
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
        BuildingResponseDto dto = new BuildingResponseDto(1L, "Edificio Central", false, null);
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
        BuildingResponseDto dto1 = new BuildingResponseDto(1L, "Edificio Central", false, null);
        BuildingResponseDto dto2 = new BuildingResponseDto(2L, "Edificio Anexo", false, null);
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

    @Test
    @DisplayName("syncBuildings: inserta el edificio que no existe con columnas de control")
    void syncBuildingsInsertsUnknownBuilding() {
        when(buildingRepository.findAll()).thenReturn(List.of());
        when(buildingRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        int affected = service.syncBuildings(List.of(new BuildingSyncCommand(4, "Edif.Malvinas")));

        ArgumentCaptor<Building> saved = ArgumentCaptor.forClass(Building.class);
        verify(buildingRepository).save(saved.capture());
        Building inserted = saved.getValue();
        assertThat(inserted.getBuildingCode()).isEqualTo(4);
        assertThat(inserted.getName()).isEqualTo("Edif.Malvinas");
        assertThat(inserted.getSyncedAt()).isNotNull();
        assertThat(inserted.getSysacadHash()).isEqualTo(Hashes.sha256Hex("Edif.Malvinas"));
        assertThat(affected).isEqualTo(1);
    }

    @Test
    @DisplayName("syncBuildings: actualiza el nombre del edificio existente cuando cambió upstream")
    void syncBuildingsUpdatesRenamedBuilding() {
        Building existing = sysacadBuilding(4, "Malvinas viejo", Hashes.sha256Hex("Malvinas viejo"));
        when(buildingRepository.findAll()).thenReturn(List.of(existing));

        int affected = service.syncBuildings(List.of(new BuildingSyncCommand(4, "Edif.Malvinas")));

        assertThat(existing.getName()).isEqualTo("Edif.Malvinas");
        assertThat(existing.getSysacadHash()).isEqualTo(Hashes.sha256Hex("Edif.Malvinas"));
        verify(buildingRepository).save(existing);
        assertThat(affected).isEqualTo(1);
    }

    @Test
    @DisplayName("syncBuildings: no escribe cuando el hash no cambió")
    void syncBuildingsSkipsUnchangedBuilding() {
        Building existing = sysacadBuilding(4, "Edif.Malvinas", Hashes.sha256Hex("Edif.Malvinas"));
        when(buildingRepository.findAll()).thenReturn(List.of(existing));

        int affected = service.syncBuildings(List.of(new BuildingSyncCommand(4, "Edif.Malvinas")));

        verify(buildingRepository, never()).save(any());
        assertThat(affected).isZero();
    }

    @Test
    @DisplayName("syncBuildings: no reactiva un edificio dado de baja que sigue en SysAcad con el mismo nombre")
    void syncBuildingsKeepsDeactivatedBuildingWithSameHash() {
        Building deactivated = sysacadBuilding(9, "Almafuerte", Hashes.sha256Hex("Almafuerte"));
        deactivated.deactivate();
        when(buildingRepository.findAll()).thenReturn(List.of(deactivated));

        int affected = service.syncBuildings(List.of(new BuildingSyncCommand(9, "Almafuerte")));

        assertThat(deactivated.getDeletedAt()).isNotNull();
        verify(buildingRepository, never()).save(any());
        assertThat(affected).isZero();
    }

    @Test
    @DisplayName("syncBuildings: actualiza el nombre de un edificio dado de baja sin reactivarlo")
    void syncBuildingsRenamesDeactivatedBuildingWithoutReactivating() {
        Building deactivated = sysacadBuilding(9, "Almafuerte", Hashes.sha256Hex("Almafuerte"));
        deactivated.deactivate();
        when(buildingRepository.findAll()).thenReturn(List.of(deactivated));

        int affected = service.syncBuildings(List.of(new BuildingSyncCommand(9, "Almafuerte Sede")));

        assertThat(deactivated.getName()).isEqualTo("Almafuerte Sede");
        assertThat(deactivated.getDeletedAt()).isNotNull();
        verify(buildingRepository).save(deactivated);
        assertThat(affected).isEqualTo(1);
    }

    @Test
    @DisplayName("syncBuildings: el edificio ausente upstream queda no vigente sin tocar deletedAt")
    void syncBuildingsMarksAbsentBuildingAsNotCurrent() {
        Building absent = sysacadBuilding(9, "Almafuerte", Hashes.sha256Hex("Almafuerte"));
        when(buildingRepository.findAll()).thenReturn(List.of(absent));

        int affected = service.syncBuildings(List.of());

        assertThat(absent.getSysacadEnabled()).isFalse();
        assertThat(absent.getDeletedAt()).isNull();
        verify(buildingRepository).save(absent);
        assertThat(affected).isEqualTo(1);
    }

    @Test
    @DisplayName("syncBuildings: un edificio no vigente que reaparece en SysAcad vuelve a vigente")
    void syncBuildingsRestoresCurrentFlagWhenBuildingReappears() {
        Building notCurrent = sysacadBuilding(9, "Almafuerte", Hashes.sha256Hex("Almafuerte"));
        notCurrent.setSysacadEnabled(false);
        when(buildingRepository.findAll()).thenReturn(List.of(notCurrent));

        int affected = service.syncBuildings(List.of(new BuildingSyncCommand(9, "Almafuerte")));

        assertThat(notCurrent.getSysacadEnabled()).isTrue();
        assertThat(notCurrent.getDeletedAt()).isNull();
        verify(buildingRepository).save(notCurrent);
        assertThat(affected).isEqualTo(1);
    }

    @Test
    @DisplayName("syncBuildings: un edificio inhabilitado y ausente upstream queda no vigente y sigue inhabilitado")
    void syncBuildingsKeepsDeactivatedBuildingDeactivatedWhenAbsent() {
        Building deactivated = sysacadBuilding(9, "Almafuerte", Hashes.sha256Hex("Almafuerte"));
        deactivated.deactivate();
        when(buildingRepository.findAll()).thenReturn(List.of(deactivated));

        int affected = service.syncBuildings(List.of());

        assertThat(deactivated.getSysacadEnabled()).isFalse();
        assertThat(deactivated.getDeletedAt()).isNotNull();
        verify(buildingRepository).save(deactivated);
        assertThat(affected).isEqualTo(1);
    }

    @Test
    @DisplayName("syncBuildings: un edificio inhabilitado y no vigente que reaparece vuelve a vigente y sigue inhabilitado")
    void syncBuildingsRestoresCurrentFlagOfDeactivatedBuildingWithoutReactivating() {
        Building deactivated = sysacadBuilding(9, "Almafuerte", Hashes.sha256Hex("Almafuerte"));
        deactivated.deactivate();
        deactivated.setSysacadEnabled(false);
        when(buildingRepository.findAll()).thenReturn(List.of(deactivated));

        int affected = service.syncBuildings(List.of(new BuildingSyncCommand(9, "Almafuerte")));

        assertThat(deactivated.getSysacadEnabled()).isTrue();
        assertThat(deactivated.getDeletedAt()).isNotNull();
        verify(buildingRepository).save(deactivated);
        assertThat(affected).isEqualTo(1);
    }

    @Test
    @DisplayName("syncBuildings: un edificio ya no vigente que sigue ausente no se escribe y no cuenta como afectado")
    void syncBuildingsIgnoresAbsentBuildingAlreadyNotCurrent() {
        Building notCurrent = sysacadBuilding(9, "Almafuerte", Hashes.sha256Hex("Almafuerte"));
        notCurrent.setSysacadEnabled(false);
        Building notCurrentDeactivated = sysacadBuilding(10, "Centro", Hashes.sha256Hex("Centro"));
        notCurrentDeactivated.setSysacadEnabled(false);
        notCurrentDeactivated.deactivate();
        when(buildingRepository.findAll()).thenReturn(List.of(notCurrent, notCurrentDeactivated));

        int affected = service.syncBuildings(List.of());

        verify(buildingRepository, never()).save(any());
        assertThat(affected).isZero();
    }

    @Test
    @DisplayName("syncBuildings: repetir la misma entrada no escribe de nuevo ni cuenta filas afectadas")
    void syncBuildingsIsIdempotentForAbsentBuilding() {
        Building absent = sysacadBuilding(9, "Almafuerte", Hashes.sha256Hex("Almafuerte"));
        when(buildingRepository.findAll()).thenReturn(List.of(absent));

        int first = service.syncBuildings(List.of());
        int second = service.syncBuildings(List.of());

        assertThat(first).isEqualTo(1);
        assertThat(second).isZero();
        verify(buildingRepository, times(1)).save(absent);
    }

    @Test
    @DisplayName("syncBuildings: repetir la reaparición de un edificio no vigente no escribe la segunda vez")
    void syncBuildingsIsIdempotentForReappearingBuilding() {
        Building notCurrent = sysacadBuilding(9, "Almafuerte", Hashes.sha256Hex("Almafuerte"));
        notCurrent.setSysacadEnabled(false);
        when(buildingRepository.findAll()).thenReturn(List.of(notCurrent));
        List<BuildingSyncCommand> input = List.of(new BuildingSyncCommand(9, "Almafuerte"));

        int first = service.syncBuildings(input);
        int second = service.syncBuildings(input);

        assertThat(first).isEqualTo(1);
        assertThat(second).isZero();
        verify(buildingRepository, times(1)).save(notCurrent);
    }

    @Test
    @DisplayName("syncBuildings: no toca los edificios sin código SysAcad (creados localmente)")
    void syncBuildingsLeavesBuildingsWithoutSysacadCodeUntouched() {
        Building local = SpaceTestData.building().id(7L).buildingCode(null).build();
        when(buildingRepository.findAll()).thenReturn(List.of(local));

        service.syncBuildings(List.of());

        assertThat(local.getDeletedAt()).isNull();
        verify(buildingRepository, never()).save(any());
    }

    private static Building sysacadBuilding(Integer code, String name, String hash) {
        return SpaceTestData.building()
                .id(code.longValue())
                .buildingCode(code)
                .name(name)
                .sysacadHash(hash)
                .build();
    }
}
