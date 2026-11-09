package ar.edu.utn.frc.siga.roomrequest.dto.response;

import java.time.LocalDate;

public record RevertedDateDto(LocalDate date, ClassroomOptionDto classroom) {}
