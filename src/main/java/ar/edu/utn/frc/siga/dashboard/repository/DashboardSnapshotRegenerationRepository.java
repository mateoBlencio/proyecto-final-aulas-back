package ar.edu.utn.frc.siga.dashboard.repository;

import ar.edu.utn.frc.siga.dashboard.model.DashboardSnapshotRegeneration;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface DashboardSnapshotRegenerationRepository extends JpaRepository<DashboardSnapshotRegeneration, Long> {
}
