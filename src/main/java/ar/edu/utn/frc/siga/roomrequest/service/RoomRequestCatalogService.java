package ar.edu.utn.frc.siga.roomrequest.service;

import ar.edu.utn.frc.siga.roomrequest.dto.response.ClassroomOptionDto;
import ar.edu.utn.frc.siga.roomrequest.dto.response.CommissionOptionDto;
import ar.edu.utn.frc.siga.roomrequest.dto.response.CommissionScheduleDto;
import ar.edu.utn.frc.siga.roomrequest.dto.response.SpecialtyOptionDto;
import ar.edu.utn.frc.siga.roomrequest.dto.response.SubjectOptionDto;

import java.util.List;

/** Lectura pública y acotada de los catálogos que necesita el formulario (no expone los controllers internos). */
public interface RoomRequestCatalogService {

    List<SpecialtyOptionDto> findSpecialties();

    List<SubjectOptionDto> findSubjectsBySpecialty(Integer specialtyCode);

    List<CommissionOptionDto> findCommissionsBySubject(Long subjectId);

    List<ClassroomOptionDto> findClassrooms();

    /** Días/horarios y fechas de cursado de una comisión. 404 si la comisión no es de la materia. */
    CommissionScheduleDto findCommissionSchedule(Long subjectId, Long commissionId);
}
