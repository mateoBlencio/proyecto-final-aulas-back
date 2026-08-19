package ar.edu.utn.frc.siga.academic.sync;

import ar.edu.utn.frc.siga.academic.model.Specialty;
import ar.edu.utn.frc.siga.academic.repository.SpecialtyRepository;
import ar.edu.utn.frc.siga.common.model.RecordSource;
import ar.edu.utn.frc.siga.common.util.Hashes;
import ar.edu.utn.frc.siga.sysacad.api.SysacadCatalogReader;
import ar.edu.utn.frc.siga.sysacad.api.SysacadSpecialtyDto;
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
@DisplayName("SpecialtySyncService")
class SpecialtySyncServiceTest {

    @Mock
    private SysacadCatalogReader catalogReader;
    @Mock
    private SpecialtyRepository specialtyRepository;
    @Mock
    private SysacadSyncStateService syncStateService;

    private SpecialtySyncService service;

    @BeforeEach
    void setUp() {
        service = new SpecialtySyncService(catalogReader, specialtyRepository, syncStateService);
    }

    @Test
    @DisplayName("sync: inserta la especialidad que no existe con origen SYSACAD y columnas de control")
    void syncInsertsUnknownSpecialty() {
        when(catalogReader.findSpecialties())
                .thenReturn(List.of(new SysacadSpecialtyDto(5, "Ingeniería en Sistemas de Información", "Ing. Sist. Inf.")));
        when(specialtyRepository.findAll()).thenReturn(List.of());
        when(specialtyRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        service.sync();

        ArgumentCaptor<Specialty> saved = ArgumentCaptor.forClass(Specialty.class);
        verify(specialtyRepository).save(saved.capture());
        Specialty inserted = saved.getValue();
        assertThat(inserted.getSpecialtyCode()).isEqualTo(5);
        assertThat(inserted.getName()).isEqualTo("Ingeniería en Sistemas de Información");
        assertThat(inserted.getSource()).isEqualTo(RecordSource.SYSACAD);
        assertThat(inserted.getSyncedAt()).isNotNull();
        assertThat(inserted.getSysacadHash()).isEqualTo(Hashes.sha256Hex("Ingeniería en Sistemas de Información"));
        assertThat(inserted.getPresentInSysacad()).isTrue();
        verify(syncStateService).recordSuccess(SysacadView.ESPECIALIDADES, 1);
    }

    @Test
    @DisplayName("sync: actualiza el nombre de la especialidad existente cuando cambió upstream")
    void syncUpdatesRenamedSpecialty() {
        Specialty existing = sysacadSpecialty(5, "Sistemas", Hashes.sha256Hex("Sistemas"));
        when(catalogReader.findSpecialties()).thenReturn(List.of(new SysacadSpecialtyDto(5, "Ing. Sistemas", "Sist.")));
        when(specialtyRepository.findAll()).thenReturn(List.of(existing));

        service.sync();

        assertThat(existing.getName()).isEqualTo("Ing. Sistemas");
        verify(specialtyRepository).save(existing);
    }

    @Test
    @DisplayName("sync: no escribe cuando el hash no cambió")
    void syncSkipsUnchangedSpecialty() {
        Specialty existing = sysacadSpecialty(5, "Ing. Sistemas", Hashes.sha256Hex("Ing. Sistemas"));
        when(catalogReader.findSpecialties()).thenReturn(List.of(new SysacadSpecialtyDto(5, "Ing. Sistemas", "Sist.")));
        when(specialtyRepository.findAll()).thenReturn(List.of(existing));

        service.sync();

        verify(specialtyRepository, never()).save(any());
        verify(syncStateService).recordSuccess(SysacadView.ESPECIALIDADES, 0);
    }

    @Test
    @DisplayName("sync: la especialidad ausente upstream queda no vigente pero no se borra")
    void syncMarksAbsentSpecialtyAsNotCurrent() {
        Specialty absent = sysacadSpecialty(5, "Ing. Sistemas", Hashes.sha256Hex("Ing. Sistemas"));
        when(catalogReader.findSpecialties()).thenReturn(List.of());
        when(specialtyRepository.findAll()).thenReturn(List.of(absent));

        service.sync();

        assertThat(absent.getPresentInSysacad()).isFalse();
        assertThat(absent.getDeleted()).isFalse();
        verify(specialtyRepository).save(absent);
    }

    @Test
    @DisplayName("sync: registra el error y propaga la excepción cuando falla la lectura")
    void syncRecordsFailure() {
        when(catalogReader.findSpecialties()).thenThrow(new IllegalStateException("SysAcad caído"));

        assertThatThrownBy(() -> service.sync()).isInstanceOf(IllegalStateException.class);

        verify(syncStateService).recordFailure(SysacadView.ESPECIALIDADES, "SysAcad caído");
    }

    private static Specialty sysacadSpecialty(Integer code, String name, String hash) {
        return Specialty.builder()
                .id(code.longValue())
                .specialtyCode(code)
                .name(name)
                .source(RecordSource.SYSACAD)
                .sysacadHash(hash)
                .presentInSysacad(true)
                .build();
    }
}
