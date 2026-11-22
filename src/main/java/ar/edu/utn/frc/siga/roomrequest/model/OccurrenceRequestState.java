package ar.edu.utn.frc.siga.roomrequest.model;

import org.springframework.modulith.NamedInterface;

@NamedInterface("api")
public enum OccurrenceRequestState {
    PENDING,
    ACCEPTED,
    CANCELLED
}
