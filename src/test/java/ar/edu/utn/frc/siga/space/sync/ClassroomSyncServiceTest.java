package ar.edu.utn.frc.siga.space.sync;

import ar.edu.utn.frc.siga.common.util.Hashes;
import ar.edu.utn.frc.siga.space.SpaceTestData;
import ar.edu.utn.frc.siga.space.model.Building;
import ar.edu.utn.frc.siga.space.model.Classroom;
import ar.edu.utn.frc.siga.space.model.ClassroomType;
import ar.edu.utn.frc.siga.space.repository.BuildingRepository;
import ar.edu.utn.frc.siga.space.repository.ClassroomRepository;
import ar.edu.utn.frc.siga.space.repository.ClassroomTypeRepository;
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
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("ClassroomSyncService")
class ClassroomSyncServiceTest {

    private static final Building BUILDING = SpaceTestData.building().id(1L).buildingCode(2).build();

    @Mock
    private SysacadCatalogReader catalogReader;
    @Mock
    private ClassroomRepository classroomRepository;
    @Mock
    private BuildingRepository buildingRepository;
    @Mock
    private ClassroomTypeRepository classroomTypeRepository;
    @Mock
    private SysacadSyncStateService syncStateService;

    private ClassroomSyncService service;

    @BeforeEach
    void setUp() {
        service = new ClassroomSyncService(
                catalogReader, classroomRepository, buildingRepository, classroomTypeRepository, syncStateService);
    }

    @Test
    @DisplayName("sync: inserta el aula nueva enlazada al edificio por su código de SysAcad, con tipo por defecto")
    void syncInsertsUnknownClassroom() {
        ClassroomType defaultType = SpaceTestData.classroomType().description("Normal").build();
        when(catalogReader.findClassrooms()).thenReturn(List.of(new SysacadClassroomDto(101, 2, true, 70)));
        when(buildingRepository.findAll()).thenReturn(List.of(BUILDING));
        when(classroomRepository.findAll()).thenReturn(List.of());
        when(classroomTypeRepository.findByDescriptionIgnoreCase("Normal")).thenReturn(Optional.of(defaultType));
        when(classroomRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        service.sync();

        ArgumentCaptor<Classroom> saved = ArgumentCaptor.forClass(Classroom.class);
        verify(classroomRepository).save(saved.capture());
        Classroom inserted = saved.getValue();
        assertThat(inserted.getRoomNumber()).isEqualTo(101);
        assertThat(inserted.getBuilding()).isSameAs(BUILDING);
        assertThat(inserted.getClassroomType()).isSameAs(defaultType);
        assertThat(inserted.getCapacity()).isEqualTo(70);
        assertThat(inserted.getSysacadEnabled()).isTrue();
        assertThat(inserted.getSysacadHash()).isEqualTo(Hashes.sha256Hex(70, true));
        verify(syncStateService).recordSuccess(SysacadView.AULAS, 1);
    }

    @Test
    @DisplayName("sync: falla con mensaje claro si falta el tipo de aula por defecto")
    void syncFailsWhenDefaultClassroomTypeMissing() {
        when(catalogReader.findClassrooms()).thenReturn(List.of(new SysacadClassroomDto(101, 2, true, 70)));
        when(buildingRepository.findAll()).thenReturn(List.of(BUILDING));
        when(classroomRepository.findAll()).thenReturn(List.of());
        when(classroomTypeRepository.findByDescriptionIgnoreCase("Normal")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.sync())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Normal");
        verify(classroomRepository, never()).save(any());
        verify(syncStateService).recordFailure(eq(SysacadView.AULAS), any());
    }

    @Test
    @DisplayName("sync: actualiza capacidad y habilitada_sysacad sin pisar el tipo de aula local")
    void syncUpdatesOnlySysacadOwnedFields() {
        ClassroomType type = SpaceTestData.classroomType().build();
        Classroom existing = sysacadClassroom(101, 40, true, Hashes.sha256Hex(40, true));
        existing.setClassroomType(type);
        when(catalogReader.findClassrooms()).thenReturn(List.of(new SysacadClassroomDto(101, 2, false, 70)));
        when(buildingRepository.findAll()).thenReturn(List.of(BUILDING));
        when(classroomRepository.findAll()).thenReturn(List.of(existing));

        service.sync();

        assertThat(existing.getCapacity()).isEqualTo(70);
        assertThat(existing.getSysacadEnabled()).isFalse();
        assertThat(existing.getClassroomType()).isSameAs(type);
        verify(classroomRepository).save(existing);
    }

    @Test
    @DisplayName("sync: no escribe cuando el hash no cambió")
    void syncSkipsUnchangedClassroom() {
        Classroom existing = sysacadClassroom(101, 70, true, Hashes.sha256Hex(70, true));
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
    @DisplayName("sync: el aula ausente upstream queda no vigente en SysAcad, sin borrarse")
    void syncMarksAbsentClassroomAsNotCurrent() {
        Classroom absent = sysacadClassroom(101, 70, true, Hashes.sha256Hex(70, true));
        when(catalogReader.findClassrooms()).thenReturn(List.of());
        when(buildingRepository.findAll()).thenReturn(List.of(BUILDING));
        when(classroomRepository.findAll()).thenReturn(List.of(absent));

        service.sync();

        assertThat(absent.getSysacadEnabled()).isFalse();
        verify(classroomRepository).save(absent);
    }

    @Test
    @DisplayName("sync: no vuelve a guardar un aula ausente que ya estaba deshabilitada")
    void syncSkipsAlreadyDisabledAbsentClassroom() {
        Classroom alreadyDisabled = sysacadClassroom(101, 70, false, Hashes.sha256Hex(70, false));
        when(catalogReader.findClassrooms()).thenReturn(List.of());
        when(buildingRepository.findAll()).thenReturn(List.of(BUILDING));
        when(classroomRepository.findAll()).thenReturn(List.of(alreadyDisabled));

        service.sync();

        verify(classroomRepository, never()).save(any());
        verify(syncStateService).recordSuccess(SysacadView.AULAS, 0);
    }

    private static Classroom sysacadClassroom(Integer roomNumber, Integer capacity, Boolean sysacadEnabled, String hash) {
        return SpaceTestData.classroom()
                .roomNumber(roomNumber)
                .capacity(capacity)
                .sysacadEnabled(sysacadEnabled)
                .building(BUILDING)
                .classroomType(null)
                .sysacadHash(hash)
                .build();
    }
}
