package ar.edu.utn.frc.siga.sysacad.internal.repository;

import ar.edu.utn.frc.siga.sysacad.internal.model.SysacadReviewReason;
import ar.edu.utn.frc.siga.sysacad.internal.model.SysacadSchedule;
import ar.edu.utn.frc.siga.sysacad.internal.model.SysacadScheduleStatus;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface SysacadScheduleRepository extends JpaRepository<SysacadSchedule, Long> {

    List<SysacadSchedule> findByBaseBuildingIsNotNullAndBaseRoomIsNotNull();

    List<SysacadSchedule> findByPresentInViewTrue();

    List<SysacadSchedule> findByStatus(SysacadScheduleStatus status);

    long countByStatus(SysacadScheduleStatus status);

    boolean existsByStatusAndReviewReason(SysacadScheduleStatus status, SysacadReviewReason reviewReason);

    @Query("""
            select s from SysacadSchedule s
            where s.presentInView = true and s.sendable = true and s.status <> :excluded
              and s.knownBuilding is not null and s.knownRoom is not null
              and (s.eventId1 is not null or s.eventId2 is not null)
            """)
    List<SysacadSchedule> findPlannable(@Param("excluded") SysacadScheduleStatus excluded);

    @Query("""
            select s from SysacadSchedule s
            where s.status = ar.edu.utn.frc.siga.sysacad.internal.model.SysacadScheduleStatus.REVIEW
               or (s.sendable = false and s.presentInView = true)
            """)
    List<SysacadSchedule> findForReviewList();
}
