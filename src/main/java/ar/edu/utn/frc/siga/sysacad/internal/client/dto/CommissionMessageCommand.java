package ar.edu.utn.frc.siga.sysacad.internal.client.dto;

import java.time.LocalDate;

public record CommissionMessageCommand(
        LocalDate visibleUntil,
        int academicYear,
        int specialty,
        int plan,
        int subject,
        int commission,
        String note
) {}
