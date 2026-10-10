package ar.edu.utn.frc.siga.dashboard.repository;

import ar.edu.utn.frc.siga.dashboard.model.DashboardSnapshot;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface DashboardSnapshotRepository extends JpaRepository<DashboardSnapshot, Long> {

    List<DashboardSnapshot> findByYearAndMonthAndCurrentTrue(int year, int month);

    List<DashboardSnapshot> findByCurrentTrueOrderByYearAscMonthAsc();

    @Modifying
    @Query("update DashboardSnapshot s set s.current = false "
            + "where s.year = :year and s.month = :month and s.current = true")
    int retire(@Param("year") int year, @Param("month") int month);
}
