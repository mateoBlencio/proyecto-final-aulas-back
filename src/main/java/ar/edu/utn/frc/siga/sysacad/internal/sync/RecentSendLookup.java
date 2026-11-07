package ar.edu.utn.frc.siga.sysacad.internal.sync;

import ar.edu.utn.frc.siga.sysacad.internal.config.SysacadProperties;
import ar.edu.utn.frc.siga.sysacad.internal.model.SysacadSchedule;
import ar.edu.utn.frc.siga.sysacad.internal.repository.SysacadScheduleLogRepository;
import java.time.Instant;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "siga.sysacad", name = "enabled", havingValue = "true")
public class RecentSendLookup {

    private final SysacadScheduleLogRepository logRepository;
    private final SysacadProperties properties;

    public boolean isRecentSend(SysacadSchedule schedule, Integer building, Integer room, Instant now) {
        if (schedule.getSendingBuilding() != null && schedule.getSendingRoom() != null
                && Objects.equals(schedule.getSendingBuilding(), building)
                && Objects.equals(schedule.getSendingRoom(), room)) {
            return true;
        }
        if (schedule.getId() == null) {
            return false;
        }
        return logRepository.existsUnconfirmedRecentIntent(schedule.getId(), building, room,
                now.minus(properties.getWrite().recentSendWindow()));
    }
}
