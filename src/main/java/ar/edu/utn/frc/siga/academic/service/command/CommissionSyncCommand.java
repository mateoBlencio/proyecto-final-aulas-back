package ar.edu.utn.frc.siga.academic.service.command;

import org.springframework.modulith.NamedInterface;

@NamedInterface("api")
public record CommissionSyncCommand(
        String courseCode,
        Integer specialtyCode,
        Integer studyPlanCode,
        Integer subjectCode,
        Integer academicYear,
        Integer enrolledCount) {
}
