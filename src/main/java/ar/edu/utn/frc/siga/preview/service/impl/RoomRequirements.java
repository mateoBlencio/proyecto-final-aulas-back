package ar.edu.utn.frc.siga.preview.service.impl;

import ar.edu.utn.frc.siga.events.dto.response.UniqueEventResponseDto;

public final class RoomRequirements {

    private RoomRequirements() {
    }

    public static int computersNeeded(UniqueEventResponseDto unique, int divisor) {
        return unique.requiresComputers() && unique.computerCount() != null
                ? Math.ceilDiv(unique.computerCount(), divisor) : 0;
    }
}
