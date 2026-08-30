package ar.edu.utn.frc.siga.academic.repository;

import ar.edu.utn.frc.siga.academic.model.Subject;
import ar.edu.utn.frc.siga.academic.model.Commission;
import ar.edu.utn.frc.siga.academic.model.SubjectCommission;
import ar.edu.utn.frc.siga.academic.model.SubjectCommissionId;
import ar.edu.utn.frc.siga.common.repository.SoftDeletableRepository;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.stereotype.Repository;

@Repository
public interface SubjectCommissionRepository
        extends SoftDeletableRepository<SubjectCommission, SubjectCommissionId> {

    @EntityGraph(attributePaths = {"commission", "commission.academicPeriod"})
    Optional<SubjectCommission> findBySubjectAndCommissionAndDeletedAtIsNull(Subject subject, Commission commission);

    @Override
    @EntityGraph(attributePaths = {"commission", "commission.academicPeriod"})
    List<SubjectCommission> findAll();

    @EntityGraph(attributePaths = {"commission", "commission.academicPeriod"})
    List<SubjectCommission> findBySubject_IdAndDeletedAtIsNull(Long subjectId);

    // TEMPORAL/defensivo: {@code findFirst...} en vez de un único resultado porque una comisión puede
    // quedar linkeada a dos materias con el mismo código bajo planes distintos (ambigüedad de datos de
    // SysAcad, ver sync de Comisiones). Se toma el de menor id_materia de forma determinística para no
    // romper EVENTOS/ASIGNACIONES. Revisar cuando se resuelva el link por plan de la comisión.
    @EntityGraph(attributePaths = {"commission", "commission.academicPeriod"})
    Optional<SubjectCommission> findFirstByCommission_IdAndSubject_CodeAndDeletedAtIsNullOrderBySubject_IdAsc(
            Long commissionId, Integer subjectCode);
}
