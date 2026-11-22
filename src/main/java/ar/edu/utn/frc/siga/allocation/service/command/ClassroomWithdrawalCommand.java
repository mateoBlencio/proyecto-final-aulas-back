package ar.edu.utn.frc.siga.allocation.service.command;

import org.springframework.modulith.NamedInterface;

import java.time.LocalDate;

@NamedInterface("api")
public record ClassroomWithdrawalCommand(Long classroomId, LocalDate from, String note) {
}
