package ar.edu.utn.frc.siga.events.config;

import java.time.LocalTime;

public interface EventScheduleSettings {

    LocalTime getStart();

    LocalTime getEnd();
}
