package ar.edu.utn.frc.siga.roomrequest.service.impl;

import java.util.List;

record DirectEventSuggestion(String suggestionId, int draftHash, List<Long> classroomIds) {
}
