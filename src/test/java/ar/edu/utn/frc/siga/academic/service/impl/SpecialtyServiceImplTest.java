package ar.edu.utn.frc.siga.academic.service.impl;

import ar.edu.utn.frc.siga.academic.mapper.SpecialtyMapper;
import ar.edu.utn.frc.siga.academic.model.Specialty;
import ar.edu.utn.frc.siga.academic.repository.SpecialtyRepository;
import ar.edu.utn.frc.siga.academic.service.command.SpecialtySyncCommand;
import ar.edu.utn.frc.siga.common.util.Hashes;
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
@DisplayName("SpecialtyServiceImpl")
class SpecialtyServiceImplTest {

    @Mock
    private SpecialtyRepository specialtyRepository;
    @Mock
    private SpecialtyMapper specialtyMapper;

    private SpecialtyServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new SpecialtyServiceImpl(specialtyRepository, specialtyMapper);
    }

    @Test
    @DisplayName("syncSpecialties: inserta la especialidad que no existe con columnas de control")
    void syncSpecialtiesInsertsUnknownSpecialty() {
        when(specialtyRepository.findAll()).thenReturn(List.of());
        when(specialtyRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        int affected = service.syncSpecialties(
                List.of(new SpecialtySyncCommand(5, "Ingeniería en Sistemas de Información", "Ing. Sist. Inf.")));

        ArgumentCaptor<Specialty> saved = ArgumentCaptor.forClass(Specialty.class);
        verify(specialtyRepository).save(saved.capture());
        Specialty inserted = saved.getValue();
        assertThat(inserted.getSpecialtyCode()).isEqualTo(5);
        assertThat(inserted.getName()).isEqualTo("Ingeniería en Sistemas de Información");
        assertThat(inserted.getAbbreviation()).isEqualTo("Ing. Sist. Inf.");
        assertThat(inserted.getSyncedAt()).isNotNull();
        assertThat(inserted.getSysacadHash())
                .isEqualTo(Hashes.sha256Hex("Ingeniería en Sistemas de Información", "Ing. Sist. Inf."));
        assertThat(affected).isEqualTo(1);
    }

    @Test
    @DisplayName("syncSpecialties: actualiza el nombre de la especialidad existente cuando cambió upstream")
    void syncSpecialtiesUpdatesRenamedSpecialty() {
        Specialty existing = sysacadSpecialty(5, "Sistemas", Hashes.sha256Hex("Sistemas", "Sist."));
        when(specialtyRepository.findAll()).thenReturn(List.of(existing));

        service.syncSpecialties(List.of(new SpecialtySyncCommand(5, "Ing. Sistemas", "Sist.")));

        assertThat(existing.getName()).isEqualTo("Ing. Sistemas");
        assertThat(existing.getAbbreviation()).isEqualTo("Sist.");
        verify(specialtyRepository).save(existing);
    }

    @Test
    @DisplayName("syncSpecialties: actualiza cuando solo cambió la abreviatura")
    void syncSpecialtiesUpdatesWhenOnlyAbbreviationChanged() {
        Specialty existing = sysacadSpecialty(5, "Ing. Sistemas", Hashes.sha256Hex("Ing. Sistemas", "Sist."));
        existing.setAbbreviation("Sist.");
        when(specialtyRepository.findAll()).thenReturn(List.of(existing));

        service.syncSpecialties(List.of(new SpecialtySyncCommand(5, "Ing. Sistemas", "Ing. Sist.")));

        assertThat(existing.getAbbreviation()).isEqualTo("Ing. Sist.");
        verify(specialtyRepository).save(existing);
    }

    @Test
    @DisplayName("syncSpecialties: no escribe cuando el hash no cambió")
    void syncSpecialtiesSkipsUnchangedSpecialty() {
        Specialty existing = sysacadSpecialty(5, "Ing. Sistemas", Hashes.sha256Hex("Ing. Sistemas", "Sist."));
        when(specialtyRepository.findAll()).thenReturn(List.of(existing));

        int affected = service.syncSpecialties(List.of(new SpecialtySyncCommand(5, "Ing. Sistemas", "Sist.")));

        verify(specialtyRepository, never()).save(any());
        assertThat(affected).isZero();
    }

    @Test
    @DisplayName("syncSpecialties: la especialidad ausente upstream no se toca (especialidad no tiene flag de vigencia)")
    void syncSpecialtiesLeavesAbsentSpecialtyUntouched() {
        Specialty absent = sysacadSpecialty(5, "Ing. Sistemas", Hashes.sha256Hex("Ing. Sistemas", "Sist."));
        when(specialtyRepository.findAll()).thenReturn(List.of(absent));

        int affected = service.syncSpecialties(List.of());

        verify(specialtyRepository, never()).save(any());
        assertThat(affected).isZero();
    }

    @Test
    @DisplayName("syncSpecialties: comando con clave vacía se ignora")
    void syncSpecialtiesIgnoresCommandWithMissingCode() {
        when(specialtyRepository.findAll()).thenReturn(List.of());

        int affected = service.syncSpecialties(List.of(new SpecialtySyncCommand(null, "Sin código", null)));

        verify(specialtyRepository, never()).save(any());
        assertThat(affected).isZero();
    }

    private static Specialty sysacadSpecialty(Integer code, String name, String hash) {
        return Specialty.builder()
                .id(code.longValue())
                .specialtyCode(code)
                .name(name)
                .sysacadHash(hash)
                .build();
    }
}
