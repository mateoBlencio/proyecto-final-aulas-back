package ar.edu.utn.frc.siga.sysacad.internal.client.dto;

public record UpdateRoomCommand(
        int academicYear,
        int specialty,
        int plan,
        int subject,
        int commission,
        int day,
        int term,
        int startHour,
        int duration,
        int building,
        int classroom
) {}
