package ar.edu.utn.frc.siga.audit.repository;

import ar.edu.utn.frc.siga.audit.model.AuditArchiveRun;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AuditArchiveRunRepository extends JpaRepository<AuditArchiveRun, Long> {
}
