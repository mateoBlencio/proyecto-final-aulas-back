package ar.edu.utn.frc.siga.sysacad.internal.service;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;

final class AllocationChangeNote {

    static final int MAX_LENGTH = 150;
    private static final String PREFIX = "Cambio de aula: ";
    private static final String SEPARATOR = "; ";
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM");
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");

    record Line(LocalDate date, LocalTime startTime, Integer fromRoom, Integer toRoom) {

        String text() {
            String when = DATE.format(date) + " " + TIME.format(startTime);
            if (toRoom == null) {
                return when + " sin aula";
            }
            return fromRoom == null ? when + " aula " + toRoom : when + " aula " + fromRoom + ">" + toRoom;
        }
    }

    private AllocationChangeNote() {
    }

    static String compose(List<Line> lines) {
        List<String> texts = lines.stream()
                .sorted(Comparator.comparing(Line::date).thenComparing(Line::startTime))
                .map(Line::text)
                .toList();
        int count = texts.size();
        String note = join(texts, count);
        while (note.length() > MAX_LENGTH && count > 1) {
            count--;
            note = join(texts, count);
        }
        return note;
    }

    private static String join(List<String> texts, int count) {
        int omitted = texts.size() - count;
        return PREFIX + String.join(SEPARATOR, texts.subList(0, count))
                + (omitted > 0 ? " y " + omitted + " más" : "");
    }
}
