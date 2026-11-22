package ar.edu.utn.frc.siga.audit.repository;

import ar.edu.utn.frc.siga.audit.model.AuditArchiveRun;
import ar.edu.utn.frc.siga.audit.model.AuditArchiveStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AuditArchiveRunRepository extends JpaRepository<AuditArchiveRun, Long> {

    List<AuditArchiveRun> findByStatusOrderByCutoffAscExecutedAtAsc(AuditArchiveStatus status);
}
