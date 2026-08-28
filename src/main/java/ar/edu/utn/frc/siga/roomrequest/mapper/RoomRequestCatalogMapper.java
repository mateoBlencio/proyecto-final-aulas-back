package ar.edu.utn.frc.siga.roomrequest.mapper;

import ar.edu.utn.frc.siga.academic.dto.response.CommissionResponseDto;
import ar.edu.utn.frc.siga.academic.dto.response.SpecialtyResponseDto;
import ar.edu.utn.frc.siga.academic.dto.response.SubjectResponseDto;
import ar.edu.utn.frc.siga.common.mapper.CentralMapperConfig;
import ar.edu.utn.frc.siga.roomrequest.dto.response.ClassroomOptionDto;
import ar.edu.utn.frc.siga.roomrequest.dto.response.CommissionOptionDto;
import ar.edu.utn.frc.siga.roomrequest.dto.response.SpecialtyOptionDto;
import ar.edu.utn.frc.siga.roomrequest.dto.response.SubjectOptionDto;
import ar.edu.utn.frc.siga.space.dto.response.ClassroomResponseDto;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

@Mapper(config = CentralMapperConfig.class)
public interface RoomRequestCatalogMapper {

    @Mapping(target = "code", source = "specialtyCode")
    SpecialtyOptionDto toOption(SpecialtyResponseDto specialty);

    List<SpecialtyOptionDto> toSpecialtyOptions(List<SpecialtyResponseDto> specialties);

    SubjectOptionDto toOption(SubjectResponseDto subject);

    List<SubjectOptionDto> toSubjectOptions(List<SubjectResponseDto> subjects);

    CommissionOptionDto toOption(CommissionResponseDto commission);

    ClassroomOptionDto toOption(ClassroomResponseDto classroom);

    List<ClassroomOptionDto> toClassroomOptions(List<ClassroomResponseDto> classrooms);
}
