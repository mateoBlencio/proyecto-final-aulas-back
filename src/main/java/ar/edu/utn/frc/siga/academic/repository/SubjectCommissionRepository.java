package ar.edu.utn.frc.siga.academic.repository;

import ar.edu.utn.frc.siga.academic.model.Subject;
import ar.edu.utn.frc.siga.academic.model.Commission;
import ar.edu.utn.frc.siga.academic.model.SubjectCommission;
import ar.edu.utn.frc.siga.academic.model.SubjectCommissionId;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface SubjectCommissionRepository extends JpaRepository<SubjectCommission, SubjectCommissionId> {

    @EntityGraph(attributePaths = {"commission", "commission.academicPeriod"})
    Optional<SubjectCommission> findBySubjectAndCommission(Subject subject, Commission commission);

    @Override
    @EntityGraph(attributePaths = {"commission", "commission.academicPeriod"})
    List<SubjectCommission> findAll();

    @EntityGraph(attributePaths = {"commission", "commission.academicPeriod"})
    List<SubjectCommission> findBySubject_Id(Long subjectId);

    // TEMPORAL/defensivo: {@code findFirst...} en vez de un único resultado porque una comisión puede
    // quedar linkeada a dos materias con el mismo código bajo planes distintos (ambigüedad de datos de
    // SysAcad, ver sync de Comisiones). Se toma el de menor id_materia de forma determinística para no
    // romper EVENTOS/ASIGNACIONES. Revisar cuando se resuelva el link por plan de la comisión.
    @EntityGraph(attributePaths = {"commission", "commission.academicPeriod"})
    Optional<SubjectCommission> findFirstByCommission_IdAndSubject_CodeOrderBySubject_IdAsc(
            Long commissionId, Integer subjectCode);
}
