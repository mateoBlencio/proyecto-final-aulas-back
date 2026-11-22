package ar.edu.utn.frc.siga.roomrequest.service.impl;

import ar.edu.utn.frc.siga.academic.dto.response.HolidayResponseDto;
import ar.edu.utn.frc.siga.academic.service.HolidayService;
import ar.edu.utn.frc.siga.settings.api.SettingsReader;
import ar.edu.utn.frc.siga.settings.model.SettingKey;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Set;
import java.util.stream.Collectors;

/** Plazo de aceptación automática: el menor entre las horas hábiles desde la notificación y el inicio del
 *  evento menos el margen. Calcula en hora argentina: la JVM corre en UTC y el día hábil es el local. */
@Component
@RequiredArgsConstructor
class AcceptanceDeadlineCalculator {

    static final ZoneId ZONE = ZoneId.of("America/Argentina/Buenos_Aires");

    private final HolidayService holidayService;
    private final SettingsReader settingsReader;

    /** @param eventStart inicio del evento en hora local, o nulo si no hay un evento al que adelantar el plazo */
    Instant calculate(Instant notifiedAt, LocalDateTime eventStart) {
        Instant byBusinessHours = addBusinessHours(notifiedAt,
                settingsReader.getInt(SettingKey.ROOM_REQUESTS_ACCEPTANCE_BUSINESS_HOURS));
        if (eventStart == null) {
            return byBusinessHours;
        }
        Instant beforeEvent = eventStart.atZone(ZONE).toInstant()
                .minus(settingsReader.getInt(SettingKey.ROOM_REQUESTS_ACCEPTANCE_HOURS_BEFORE_EVENT), ChronoUnit.HOURS);
        return beforeEvent.isBefore(byBusinessHours) ? beforeEvent : byBusinessHours;
    }

    /** Cada día hábil que pasa suma 24 horas; las horas que sobran de un día completo se suman corridas al final. */
    private Instant addBusinessHours(Instant from, int hours) {
        ZonedDateTime start = from.atZone(ZONE);
        int businessDays = hours / 24;
        Set<LocalDate> holidays = holidayService
                .findBetween(start.toLocalDate().plusDays(1), start.toLocalDate().plusDays(businessDays * 7L + 60))
                .stream().map(HolidayResponseDto::date).collect(Collectors.toSet());

        LocalDate day = start.toLocalDate();
        int counted = 0;
        while (counted < businessDays) {
            day = day.plusDays(1);
            if (isBusinessDay(day, holidays)) {
                counted++;
            }
        }
        return start.with(day).plusHours(hours % 24).toInstant();
    }

    private boolean isBusinessDay(LocalDate day, Set<LocalDate> holidays) {
        DayOfWeek dow = day.getDayOfWeek();
        return dow != DayOfWeek.SATURDAY && dow != DayOfWeek.SUNDAY && !holidays.contains(day);
    }
}
