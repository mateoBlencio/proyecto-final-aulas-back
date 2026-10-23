package ar.edu.utn.frc.siga.academic.dto;

public record CommissionFilter(
        String courseCode,
        Long academicPeriodId,
        Integer specialtyCode,
        Long subjectId,
        Boolean withoutSpecialty,
        String q
) {
    public CommissionFilter(String courseCode, Long academicPeriodId, Integer specialtyCode, Long subjectId) {
        this(courseCode, academicPeriodId, specialtyCode, subjectId, null, null);
    }

    public CommissionFilter(String courseCode, Long academicPeriodId, Integer specialtyCode, Long subjectId,
            Boolean withoutSpecialty) {
        this(courseCode, academicPeriodId, specialtyCode, subjectId, withoutSpecialty, null);
    }
}
