package ar.edu.utn.frc.siga.space.sync;

import ar.edu.utn.frc.siga.common.model.RecordSource;
import ar.edu.utn.frc.siga.common.util.Hashes;
import ar.edu.utn.frc.siga.space.SpaceTestData;
import ar.edu.utn.frc.siga.space.model.Building;
import ar.edu.utn.frc.siga.space.model.Classroom;
import ar.edu.utn.frc.siga.space.model.ClassroomType;
import ar.edu.utn.frc.siga.space.repository.BuildingRepository;
import ar.edu.utn.frc.siga.space.repository.ClassroomRepository;
import ar.edu.utn.frc.siga.sysacad.api.SysacadCatalogReader;
import ar.edu.utn.frc.siga.sysacad.api.SysacadClassroomDto;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("ClassroomSyncService")
class ClassroomSyncServiceTest {

    private static final Building BUILDING = SpaceTestData.building().id(1).buildingCode(2).build();

    @Mock
    private SysacadCatalogReader catalogReader;
    @Mock
    private ClassroomRepository classroomRepository;
    @Mock
    private BuildingRepository buildingRepository;
    @Mock
    private SysacadSyncStateService syncStateService;

    private ClassroomSyncService service;

    @BeforeEach
    void setUp() {
        service = new ClassroomSyncService(catalogReader, classroomRepository, buildingRepository, syncStateService);
    }

    @Test
    @DisplayName("sync: inserta el aula nueva enlazada al edificio por su código de SysAcad")
    void syncInsertsUnknownClassroom() {
        when(catalogReader.findClassrooms()).thenReturn(List.of(new SysacadClassroomDto(101, 2, true, 70)));
        when(buildingRepository.findAll()).thenReturn(List.of(BUILDING));
        when(classroomRepository.findAll()).thenReturn(List.of());
        when(classroomRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        service.sync();

        ArgumentCaptor<Classroom> saved = ArgumentCaptor.forClass(Classroom.class);
        verify(classroomRepository).save(saved.capture());
        Classroom inserted = saved.getValue();
        assertThat(inserted.getRoomNumber()).isEqualTo("101");
        assertThat(inserted.getBuilding()).isSameAs(BUILDING);
        assertThat(inserted.getCapacity()).isEqualTo(70);
        assertThat(inserted.getAvailable()).isTrue();
        assertThat(inserted.getSource()).isEqualTo(RecordSource.SYSACAD);
        assertThat(inserted.getSysacadHash()).isEqualTo(Hashes.sha256Hex(70, true));
        assertThat(inserted.getPresentInSysacad()).isTrue();
        verify(syncStateService).recordSuccess(SysacadView.AULAS, 1);
    }

    @Test
    @DisplayName("sync: actualiza capacidad y disponibilidad sin pisar piso ni tipo de aula")
    void syncUpdatesOnlySysacadOwnedFields() {
        ClassroomType type = SpaceTestData.classroomType().build();
        Classroom existing = sysacadClassroom("101", 40, true, Hashes.sha256Hex(40, true));
        existing.setFloor(3);
        existing.setClassroomType(type);
        when(catalogReader.findClassrooms()).thenReturn(List.of(new SysacadClassroomDto(101, 2, false, 70)));
        when(buildingRepository.findAll()).thenReturn(List.of(BUILDING));
        when(classroomRepository.findAll()).thenReturn(List.of(existing));

        service.sync();

        assertThat(existing.getCapacity()).isEqualTo(70);
        assertThat(existing.getAvailable()).isFalse();
        assertThat(existing.getFloor()).isEqualTo(3);
        assertThat(existing.getClassroomType()).isSameAs(type);
        verify(classroomRepository).save(existing);
    }

    @Test
    @DisplayName("sync: no escribe cuando el hash no cambió")
    void syncSkipsUnchangedClassroom() {
        Classroom existing = sysacadClassroom("101", 70, true, Hashes.sha256Hex(70, true));
        when(catalogReader.findClassrooms()).thenReturn(List.of(new SysacadClassroomDto(101, 2, true, 70)));
        when(buildingRepository.findAll()).thenReturn(List.of(BUILDING));
        when(classroomRepository.findAll()).thenReturn(List.of(existing));

        service.sync();

        verify(classroomRepository, never()).save(any());
        verify(syncStateService).recordSuccess(SysacadView.AULAS, 0);
    }

    @Test
    @DisplayName("sync: ignora el aula cuyo edificio no está replicado")
    void syncSkipsClassroomWithUnknownBuilding() {
        when(catalogReader.findClassrooms()).thenReturn(List.of(new SysacadClassroomDto(101, 99, true, 70)));
        when(buildingRepository.findAll()).thenReturn(List.of(BUILDING));
        when(classroomRepository.findAll()).thenReturn(List.of());

        service.sync();

        verify(classroomRepository, never()).save(any());
        verify(syncStateService).recordSuccess(SysacadView.AULAS, 0);
    }

    @Test
    @DisplayName("sync: el aula ausente upstream queda no disponible y no vigente, sin borrarse")
    void syncMarksAbsentClassroomAsNotCurrent() {
        Classroom absent = sysacadClassroom("101", 70, true, Hashes.sha256Hex(70, true));
        when(catalogReader.findClassrooms()).thenReturn(List.of());
        when(buildingRepository.findAll()).thenReturn(List.of(BUILDING));
        when(classroomRepository.findAll()).thenReturn(List.of(absent));

        service.sync();

        assertThat(absent.getPresentInSysacad()).isFalse();
        assertThat(absent.getAvailable()).isFalse();
        assertThat(absent.getDeleted()).isFalse();
        verify(classroomRepository).save(absent);
    }

    @Test
    @DisplayName("sync: no toca las aulas locales ausentes de SysAcad")
    void syncLeavesLocalClassroomsUntouched() {
        Classroom local = SpaceTestData.classroom().building(BUILDING).source(RecordSource.LOCAL).build();
        when(catalogReader.findClassrooms()).thenReturn(List.of());
        when(buildingRepository.findAll()).thenReturn(List.of(BUILDING));
        when(classroomRepository.findAll()).thenReturn(List.of(local));

        service.sync();

        assertThat(local.getAvailable()).isTrue();
        assertThat(local.getPresentInSysacad()).isTrue();
        verify(classroomRepository, never()).save(any());
    }

    private static Classroom sysacadClassroom(String roomNumber, Integer capacity, Boolean available, String hash) {
        return SpaceTestData.classroom()
                .roomNumber(roomNumber)
                .capacity(capacity)
                .available(available)
                .building(BUILDING)
                .classroomType(null)
                .floor(null)
                .source(RecordSource.SYSACAD)
                .sysacadHash(hash)
                .presentInSysacad(true)
                .build();
    }
}
