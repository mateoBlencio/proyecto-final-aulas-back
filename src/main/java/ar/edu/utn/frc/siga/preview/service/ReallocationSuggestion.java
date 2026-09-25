package ar.edu.utn.frc.siga.preview.service;

import java.util.List;

public record ReallocationSuggestion(String suggestionId, Long eventId, List<Long> occurrenceIds, Long classroomId) {
}
