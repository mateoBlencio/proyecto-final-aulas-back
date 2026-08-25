package ar.edu.utn.frc.siga.space.sync;

import ar.edu.utn.frc.siga.common.util.Hashes;
import ar.edu.utn.frc.siga.space.SpaceTestData;
import ar.edu.utn.frc.siga.space.model.Building;
import ar.edu.utn.frc.siga.space.repository.BuildingRepository;
import ar.edu.utn.frc.siga.sysacad.api.SysacadBuildingDto;
import ar.edu.utn.frc.siga.sysacad.api.SysacadCatalogReader;
import ar.edu.utn.frc.siga.sysacad.api.SysacadSyncStateService;
import ar.edu.utn.frc.siga.sysacad.api.SysacadView;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("BuildingSyncService")
class BuildingSyncServiceTest {

    @Mock
    private SysacadCatalogReader catalogReader;
    @Mock
    private BuildingRepository buildingRepository;
    @Mock
    private SysacadSyncStateService syncStateService;

    private BuildingSyncService service;

    @BeforeEach
    void setUp() {
        service = new BuildingSyncService(catalogReader, buildingRepository, syncStateService);
    }

    @Test
    @DisplayName("sync: inserta el edificio que no existe con columnas de control")
    void syncInsertsUnknownBuilding() {
        when(catalogReader.findBuildings()).thenReturn(List.of(new SysacadBuildingDto(4, "Edif.Malvinas")));
        when(buildingRepository.findAll()).thenReturn(List.of());
        when(buildingRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        service.sync();

        ArgumentCaptor<Building> saved = ArgumentCaptor.forClass(Building.class);
        verify(buildingRepository).save(saved.capture());
        Building inserted = saved.getValue();
        assertThat(inserted.getBuildingCode()).isEqualTo(4);
        assertThat(inserted.getName()).isEqualTo("Edif.Malvinas");
        assertThat(inserted.getSyncedAt()).isNotNull();
        assertThat(inserted.getSysacadHash()).isEqualTo(Hashes.sha256Hex("Edif.Malvinas"));
        verify(syncStateService).recordSuccess(SysacadView.EDIFICIOS, 1);
    }

    @Test
    @DisplayName("sync: actualiza el nombre del edificio existente cuando cambió upstream")
    void syncUpdatesRenamedBuilding() {
        Building existing = sysacadBuilding(4, "Malvinas viejo", Hashes.sha256Hex("Malvinas viejo"));
        when(catalogReader.findBuildings()).thenReturn(List.of(new SysacadBuildingDto(4, "Edif.Malvinas")));
        when(buildingRepository.findAll()).thenReturn(List.of(existing));

        service.sync();

        assertThat(existing.getName()).isEqualTo("Edif.Malvinas");
        assertThat(existing.getSysacadHash()).isEqualTo(Hashes.sha256Hex("Edif.Malvinas"));
        verify(buildingRepository).save(existing);
        verify(syncStateService).recordSuccess(SysacadView.EDIFICIOS, 1);
    }

    @Test
    @DisplayName("sync: no escribe cuando el hash no cambió")
    void syncSkipsUnchangedBuilding() {
        Building existing = sysacadBuilding(4, "Edif.Malvinas", Hashes.sha256Hex("Edif.Malvinas"));
        when(catalogReader.findBuildings()).thenReturn(List.of(new SysacadBuildingDto(4, "Edif.Malvinas")));
        when(buildingRepository.findAll()).thenReturn(List.of(existing));

        service.sync();

        verify(buildingRepository, never()).save(any());
        verify(syncStateService).recordSuccess(SysacadView.EDIFICIOS, 0);
    }

    @Test
    @DisplayName("sync: el edificio ausente upstream queda no vigente y con deletedAt, sin borrarse")
    void syncMarksAbsentBuildingAsNotCurrent() {
        Building absent = sysacadBuilding(9, "Almafuerte", Hashes.sha256Hex("Almafuerte"));
        when(catalogReader.findBuildings()).thenReturn(List.of());
        when(buildingRepository.findAll()).thenReturn(List.of(absent));

        service.sync();

        assertThat(absent.getDeletedAt()).isNotNull();
        verify(buildingRepository).save(absent);
        verify(syncStateService).recordSuccess(SysacadView.EDIFICIOS, 1);
    }

    @Test
    @DisplayName("sync: no toca los edificios sin código SysAcad (creados localmente)")
    void syncLeavesBuildingsWithoutSysacadCodeUntouched() {
        Building local = SpaceTestData.building().id(7L).buildingCode(null).build();
        when(catalogReader.findBuildings()).thenReturn(List.of());
        when(buildingRepository.findAll()).thenReturn(List.of(local));

        service.sync();

        assertThat(local.getDeletedAt()).isNull();
        verify(buildingRepository, never()).save(any());
    }

    @Test
    @DisplayName("sync: registra el error y propaga la excepción cuando falla la lectura")
    void syncRecordsFailure() {
        when(catalogReader.findBuildings()).thenThrow(new IllegalStateException("SysAcad caído"));

        assertThatThrownBy(() -> service.sync()).isInstanceOf(IllegalStateException.class);

        verify(syncStateService).recordFailure(SysacadView.EDIFICIOS, "SysAcad caído");
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
