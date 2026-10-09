package ar.edu.utn.frc.siga.roomrequest.service.impl;

import java.util.List;

record RoomRequestSuggestion(String suggestionId, Long itemId, Long itemVersion, List<Long> classroomIds) {
}
