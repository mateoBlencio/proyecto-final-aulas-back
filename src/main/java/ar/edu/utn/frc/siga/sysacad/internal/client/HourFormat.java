package ar.edu.utn.frc.siga.sysacad.internal.client;

import java.time.LocalTime;

public final class HourFormat {

    private HourFormat() {}

    public static int toProcedureHour(String startTime) {
        LocalTime time = LocalTime.parse(startTime.trim());
        return time.getHour() * 100 + time.getMinute();
    }
}
