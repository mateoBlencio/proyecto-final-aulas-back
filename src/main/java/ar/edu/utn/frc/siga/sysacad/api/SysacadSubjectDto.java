package ar.edu.utn.frc.siga.sysacad.api;

public record SysacadSubjectDto(
        Integer specialtyCode,
        Integer studyPlanCode,
        Integer subjectCode,
        String name,
        String term
) {}
