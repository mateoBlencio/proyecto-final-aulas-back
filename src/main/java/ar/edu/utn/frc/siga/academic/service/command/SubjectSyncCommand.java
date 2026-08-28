package ar.edu.utn.frc.siga.academic.service.command;

import org.springframework.modulith.NamedInterface;

@NamedInterface("api")
public record SubjectSyncCommand(
        Integer specialtyCode,
        Integer studyPlanCode,
        Integer subjectCode,
        String name,
        String term) {
}
