package ar.edu.utn.frc.siga.sysacad.internal.write.plan;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

public record RoomRef(Integer building, Integer room) {

    private static final Comparator<RoomRef> ORDER =
            Comparator.comparing(RoomRef::building).thenComparing(RoomRef::room);

    public static String key(List<RoomRef> rooms) {
        return rooms.stream().sorted(ORDER).map(r -> r.building() + "-" + r.room())
                .collect(Collectors.joining(","));
    }

    public static String keyOf(Integer building, Integer room) {
        return building == null || room == null ? null : building + "-" + room;
    }
}
