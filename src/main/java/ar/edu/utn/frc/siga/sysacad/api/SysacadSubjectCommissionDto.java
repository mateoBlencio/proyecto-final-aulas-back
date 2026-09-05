package ar.edu.utn.frc.siga.sysacad.api;

public record SysacadSubjectCommissionDto(
        String courseCode,
        Integer subjectCode,
        Integer enrolledCount
) {}
