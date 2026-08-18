package ar.edu.utn.frc.siga.sysacad.internal;

import ar.edu.utn.frc.siga.sysacad.api.SysacadBuildingDto;
import ar.edu.utn.frc.siga.sysacad.api.SysacadClassroomDto;
import ar.edu.utn.frc.siga.sysacad.api.SysacadCommissionDto;
import ar.edu.utn.frc.siga.sysacad.api.SysacadSpecialtyDto;
import ar.edu.utn.frc.siga.sysacad.internal.dto.RawBuilding;
import ar.edu.utn.frc.siga.sysacad.internal.dto.RawClassroom;
import ar.edu.utn.frc.siga.sysacad.internal.dto.RawCommission;
import ar.edu.utn.frc.siga.sysacad.internal.dto.RawSpecialty;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("SysacadCatalogMapper")
class SysacadCatalogMapperTest {

    private final SysacadCatalogMapper mapper = new SysacadCatalogMapper();

    @Test
    @DisplayName("toBuilding: recorta el relleno de espacios del nombre")
    void recortaNombreDeEdificio() {
        SysacadBuildingDto building = mapper.toBuilding(
                new RawBuilding(2, "Edif.Central                            "));

        assertThat(building).isEqualTo(new SysacadBuildingDto(2, "Edif.Central"));
    }

    @Test
    @DisplayName("toClassroom: 'S' habilitada, cualquier otro valor no")
    void traduceHabilitada() {
        assertThat(mapper.toClassroom(new RawClassroom(101, 2, "S", 40)))
                .isEqualTo(new SysacadClassroomDto(101, 2, true, 40));
        assertThat(mapper.toClassroom(new RawClassroom(0, 1, "N", 0)))
                .isEqualTo(new SysacadClassroomDto(0, 1, false, 0));
    }

    @Test
    @DisplayName("toClassroom: habilitada nula se toma como no habilitada")
    void habilitadaNulaEsFalse() {
        assertThat(mapper.toClassroom(new RawClassroom(101, 2, null, 40)).isEnabled()).isFalse();
    }

    @Test
    @DisplayName("toSpecialty: recorta nombre y abreviatura")
    void recortaEspecialidad() {
        SysacadSpecialtyDto specialty = mapper.toSpecialty(new RawSpecialty(
                5,
                "Ingeniería en Sistemas de Información                       ",
                "Ing. Sist. Inf."));

        assertThat(specialty).isEqualTo(
                new SysacadSpecialtyDto(5, "Ingeniería en Sistemas de Información", "Ing. Sist. Inf."));
    }

    @Test
    @DisplayName("toCommission: recorta el código de curso y conserva los códigos numéricos")
    void recortaCodigoDeCurso() {
        SysacadCommissionDto commission = mapper.toCommission(
                new RawCommission("5S1   ", 17, 94, 519, 2026, 10));

        assertThat(commission).isEqualTo(new SysacadCommissionDto("5S1", 17, 94, 519, 2026, 10));
    }
}
