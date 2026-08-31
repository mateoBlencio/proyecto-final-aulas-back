package ar.edu.utn.frc.siga.roomrequest.dto.request;

import java.time.LocalTime;
import java.util.List;

final class RoomRequestItemChecks {

    private RoomRequestItemChecks() {
    }

    static boolean timeRangeValid(LocalTime startTime, LocalTime endTime) {
        return startTime == null || endTime == null || endTime.isAfter(startTime);
    }

    static boolean computerCountConsistent(Boolean requiresComputers, Integer computerCount) {
        return Boolean.TRUE.equals(requiresComputers) == (computerCount != null);
    }

    static boolean requiredSoftwareConsistent(Boolean requiresComputers, String requiredSoftware) {
        return requiredSoftware == null || Boolean.TRUE.equals(requiresComputers);
    }

    static boolean preferencesDistinct(List<Long> preferredClassroomIds) {
        return preferredClassroomIds == null
                || preferredClassroomIds.size() == preferredClassroomIds.stream().distinct().count();
    }
}
