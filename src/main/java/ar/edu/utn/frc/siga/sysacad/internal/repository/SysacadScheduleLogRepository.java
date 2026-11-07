package ar.edu.utn.frc.siga.sysacad.internal.repository;

import ar.edu.utn.frc.siga.sysacad.internal.model.ScheduleLogType;
import ar.edu.utn.frc.siga.sysacad.internal.model.SysacadScheduleLogEntry;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface SysacadScheduleLogRepository extends JpaRepository<SysacadScheduleLogEntry, Long> {

    Optional<SysacadScheduleLogEntry> findFirstByScheduleIdAndTypeOrderByIdDesc(Long scheduleId, ScheduleLogType type);

    @Query("""
            select count(e) > 0 from SysacadScheduleLogEntry e
            where e.scheduleId = :scheduleId and e.type = ar.edu.utn.frc.siga.sysacad.internal.model.ScheduleLogType.SEND_INTENT
              and e.newBuilding = :building and e.newRoom = :room and e.occurredAt >= :since
            """)
    boolean existsRecentIntent(@Param("scheduleId") Long scheduleId, @Param("building") Integer building,
            @Param("room") Integer room, @Param("since") Instant since);

    Page<SysacadScheduleLogEntry> findByScheduleId(Long scheduleId, Pageable pageable);

    @Query("""
            select e from SysacadScheduleLogEntry e
            where e.type = ar.edu.utn.frc.siga.sysacad.internal.model.ScheduleLogType.SEND_CONFIRMED
              and e.scheduleId in :scheduleIds
              and e.id = (select max(e2.id) from SysacadScheduleLogEntry e2
                          where e2.scheduleId = e.scheduleId
                            and e2.type = ar.edu.utn.frc.siga.sysacad.internal.model.ScheduleLogType.SEND_CONFIRMED)
            """)
    List<SysacadScheduleLogEntry> findLastConfirmedSends(@Param("scheduleIds") Collection<Long> scheduleIds);

    @Query("""
            select e from SysacadScheduleLogEntry e
            where e.type in :types and e.occurredAt > :after and e.occurredAt <= :until
            order by e.id
            """)
    List<SysacadScheduleLogEntry> findBetween(@Param("types") Collection<ScheduleLogType> types,
            @Param("after") Instant after, @Param("until") Instant until);
}
