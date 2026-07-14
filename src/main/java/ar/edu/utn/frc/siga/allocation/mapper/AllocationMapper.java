package ar.edu.utn.frc.siga.allocation.mapper;

import ar.edu.utn.frc.siga.allocation.dto.response.AcademicEventResponseDto;
import ar.edu.utn.frc.siga.allocation.dto.response.AllocationResponseDto;
import ar.edu.utn.frc.siga.allocation.model.Allocation;
import ar.edu.utn.frc.siga.common.mapper.CentralMapperConfig;
import ar.edu.utn.frc.siga.space.dto.response.ClassroomResponseDto;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/**
 * Mapper puro: solo mapea los campos propios de {@link Allocation} (incluida la
 * ocurrencia, intra-módulo, vía {@link OccurrenceMapper}). Los datos ajenos (evento
 * académico compuesto, aula) se resuelven afuera, en {@link AllocationComposer}, y se
 * pasan como parámetros — mismo patrón que {@link AcademicEventMapper}.
 */
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
