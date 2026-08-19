package ar.edu.utn.frc.siga.sysacad.internal.mapper;

import ar.edu.utn.frc.siga.sysacad.api.SysacadBuildingDto;
import ar.edu.utn.frc.siga.sysacad.api.SysacadClassroomDto;
import ar.edu.utn.frc.siga.sysacad.api.SysacadCommissionDto;
import ar.edu.utn.frc.siga.sysacad.api.SysacadSpecialtyDto;
import ar.edu.utn.frc.siga.sysacad.internal.client.dto.RawBuilding;
import ar.edu.utn.frc.siga.sysacad.internal.client.dto.RawClassroom;
import ar.edu.utn.frc.siga.sysacad.internal.client.dto.RawCommission;
import ar.edu.utn.frc.siga.sysacad.internal.client.dto.RawSpecialty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "siga.sysacad", name = "enabled", havingValue = "true")
public class SysacadCatalogMapper {

    private static final String YES = "S";

    public SysacadBuildingDto toBuilding(RawBuilding raw) {
        return new SysacadBuildingDto(raw.edificio(), trim(raw.edificioNombre()));
    }

    public SysacadClassroomDto toClassroom(RawClassroom raw) {
        return new SysacadClassroomDto(
                raw.aula(),
                raw.edificio(),
                YES.equalsIgnoreCase(trim(raw.habilitada())),
                raw.capacidad());
    }

    public SysacadSpecialtyDto toSpecialty(RawSpecialty raw) {
        return new SysacadSpecialtyDto(
                raw.especialid(),
                trim(raw.asEspecialidadNombre()),
                trim(raw.abreviatura()));
    }

    public SysacadCommissionDto toCommission(RawCommission raw) {
        return new SysacadCommissionDto(
                trim(raw.curso()),
                raw.especialid(),
                raw.plan(),
                raw.materia(),
                raw.anoacademi(),
                raw.comision());
    }

    private static String trim(String value) {
        return value == null ? null : value.trim();
    }
}
