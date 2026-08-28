package ar.edu.utn.frc.siga.academic.service;

import ar.edu.utn.frc.siga.academic.dto.response.CommissionResponseDto;
import ar.edu.utn.frc.siga.academic.service.command.CommissionSyncCommand;
import java.util.Collection;
import java.util.List;

import org.springframework.modulith.NamedInterface;

@NamedInterface("api")
public interface CommissionService {

    CommissionResponseDto findById(Long id);

    List<CommissionResponseDto> findByIds(Collection<Long> ids);

    List<CommissionResponseDto> findAll();

    CommissionResponseDto findByCourseAndPeriod(String courseCode, Integer periodYear, Integer periodSemester);

    /**
     * Resuelve la comisión vigente por código de curso, sin conocer el año de antemano: a lo sumo hay
     * una comisión {@code sysacadEnabled=true} por curso (supuesto documentado en
     * supuestos-sync-horarios.md §1). El año sale de {@code academicPeriod().year()} de la comisión
     * devuelta. Ausente o ambigua (más de una vigente para el mismo curso) → 404 catcheable, para que
     * el sync de Eventos salte la fila con WARN.
     */
    CommissionResponseDto findActiveByCourseCode(String courseCode);

    /**
     * Sincroniza el lote de comisiones provenientes de SysAcad: crea/actualiza cada comisión (bajo
     * su período anual), enlaza materia-comisión con los inscriptos entrantes y marca como no
     * vigentes las comisiones ausentes en la corrida. Devuelve la cantidad de filas afectadas.
     */
    int syncCommissions(List<CommissionSyncCommand> commands);
}
