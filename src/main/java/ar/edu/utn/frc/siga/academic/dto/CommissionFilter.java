package ar.edu.utn.frc.siga.academic.dto;

public record CommissionFilter(
        String courseCode,
        Long academicPeriodId,
        Integer specialtyCode,
        Long subjectId
) {
}
