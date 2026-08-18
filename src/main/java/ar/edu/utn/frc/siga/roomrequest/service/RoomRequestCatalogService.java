package ar.edu.utn.frc.siga.roomrequest.service;

import ar.edu.utn.frc.siga.roomrequest.dto.response.ClassroomOptionDto;
import ar.edu.utn.frc.siga.roomrequest.dto.response.CommissionOptionDto;
import ar.edu.utn.frc.siga.roomrequest.dto.response.SpecialtyOptionDto;
import ar.edu.utn.frc.siga.roomrequest.dto.response.SubjectOptionDto;

import java.util.List;

/**
 * Lectura pública y acotada de los catálogos que necesita el formulario.
 *
 * <p>No expone los controllers de {@code academic} y {@code space}, que están
 * cerrados por rol para personal interno: consume sus fachadas {@code api} y
 * devuelve solo los campos necesarios para poblar los combos.
 */
public interface RoomRequestCatalogService {

    List<SpecialtyOptionDto> findSpecialties();

    List<SubjectOptionDto> findSubjectsBySpecialty(Integer specialtyCode);

    List<CommissionOptionDto> findCommissionsBySubject(Long subjectId);

    List<ClassroomOptionDto> findClassrooms();
}
