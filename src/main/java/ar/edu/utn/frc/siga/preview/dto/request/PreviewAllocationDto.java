package ar.edu.utn.frc.siga.preview.dto.request;

import jakarta.validation.constraints.NotNull;

public record PreviewAllocationDto(@NotNull Long eventId, Integer roomSlot, Long classroomId) {

    public int effectiveRoomSlot() {
        return roomSlot != null ? roomSlot : 1;
    }
}
