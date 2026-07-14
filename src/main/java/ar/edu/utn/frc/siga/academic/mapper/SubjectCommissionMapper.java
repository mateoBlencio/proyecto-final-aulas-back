package ar.edu.utn.frc.siga.academic.mapper;

import ar.edu.utn.frc.siga.academic.dto.response.SubjectCommissionResponseDto;
import ar.edu.utn.frc.siga.academic.model.SubjectCommission;
import ar.edu.utn.frc.siga.common.mapper.CentralMapperConfig;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/** Mapea {@link SubjectCommission} hacia su DTO, aplanando materia y comisión a su ID. */
@Mapper(config = CentralMapperConfig.class)
public interface SubjectCommissionMapper {

    @Mapping(target = "subjectId", source = "subject.id")
    @Mapping(target = "commissionId", source = "commission.id")
    SubjectCommissionResponseDto toDto(SubjectCommission subjectCommission);
}
