package ar.edu.utn.frc.siga.sysacad.api;

public record SysacadSubjectCommissionDto(
        String courseCode,
        Integer specialtyCode,
        Integer studyPlanCode,
        Integer subjectCode,
        Integer enrolledCount
) {}
