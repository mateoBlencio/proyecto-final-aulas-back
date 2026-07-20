package ar.edu.utn.frc.siga.allocation.mapper;

import ar.edu.utn.frc.siga.allocation.dto.response.AcademicEventResponseDto;
import ar.edu.utn.frc.siga.allocation.dto.response.AllocationResponseDto;
import ar.edu.utn.frc.siga.allocation.model.Allocation;
import ar.edu.utn.frc.siga.common.mapper.CentralMapperConfig;
import ar.edu.utn.frc.siga.space.dto.response.ClassroomResponseDto;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/** Mapper puro: solo mapea los campos propios de la asignación. Datos ajenos (evento, aula) se pasan resueltos como parámetros. */
@Mapper(config = CentralMapperConfig.class, uses = OccurrenceMapper.class)
public interface AllocationMapper {

    // "event"/"classroom" se fuerzan a mapear el parámetro entero: allocation solo tiene
    // classroomId (Integer) — el ClassroomResponseDto siempre viene resuelto por el
    // composer, nunca navegando la entidad.
    @Mapping(target = "id", source = "allocation.id")
    @Mapping(target = "event", source = "event")
    @Mapping(target = "classroom", source = "classroom")
    AllocationResponseDto toDto(Allocation allocation, AcademicEventResponseDto event, ClassroomResponseDto classroom);
}
