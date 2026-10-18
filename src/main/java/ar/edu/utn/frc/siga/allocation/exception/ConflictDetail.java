package ar.edu.utn.frc.siga.allocation.exception;

import ar.edu.utn.frc.siga.allocation.dto.response.OccurrenceConflictDto;

import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

final class ConflictDetail {

    private static final int MAX_LINES = 3;
    private static final DateTimeFormatter DAY_MONTH = DateTimeFormatter.ofPattern("dd/MM");

    private ConflictDetail() {
    }

    static List<Long> classroomIds(List<OccurrenceConflictDto> conflicts) {
        return conflicts.stream().map(OccurrenceConflictDto::classroomId).distinct().toList();
    }

    static String describe(List<OccurrenceConflictDto> conflicts, Map<Long, Integer> roomNumbers) {
        String lines = conflicts.stream()
                .limit(MAX_LINES)
                .map(c -> "El aula " + roomNumbers.getOrDefault(c.classroomId(), c.classroomId().intValue())
                        + " se superpone " + c.overlapMinutes() + " min con otro evento el "
                        + DAY_MONTH.format(c.date()) + ".")
                .collect(Collectors.joining("\n"));
        int more = conflicts.size() - MAX_LINES;
        return more > 0 ? lines + "\ny " + more + " más." : lines;
    }
}
