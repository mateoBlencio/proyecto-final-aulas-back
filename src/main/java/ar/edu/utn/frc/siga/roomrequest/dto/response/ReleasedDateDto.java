package ar.edu.utn.frc.siga.roomrequest.dto.response;

import ar.edu.utn.frc.siga.events.model.ReleaseReason;

import java.time.LocalDate;

public record ReleasedDateDto(LocalDate date, ReleaseReason reason) {}
