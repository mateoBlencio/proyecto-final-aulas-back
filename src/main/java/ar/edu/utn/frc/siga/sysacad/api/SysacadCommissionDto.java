package ar.edu.utn.frc.siga.sysacad.api;

public record SysacadCommissionDto(
        String courseCode,
        Integer specialtyCode,
        Integer studyPlanCode,
        Integer subjectCode,
        Integer academicYear,
        Integer commissionNumber
) {}
