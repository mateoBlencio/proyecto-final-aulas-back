package ar.edu.utn.frc.siga.academic.dto;

import org.springframework.modulith.NamedInterface;

@NamedInterface("api")
public record SpecialtyFilter(
        Integer specialtyCode,
        String name,
        Boolean hasSubjects,
        String commissionCourseCode,
        Long commissionAcademicPeriodId,
        Integer subjectCode,
        String subjectName,
        Long studyPlanId,
        String q
) {
    public SpecialtyFilter(Integer specialtyCode, String name, Boolean hasSubjects, String commissionCourseCode,
            Long commissionAcademicPeriodId, Integer subjectCode, String subjectName, Long studyPlanId) {
        this(specialtyCode, name, hasSubjects, commissionCourseCode, commissionAcademicPeriodId, subjectCode,
                subjectName, studyPlanId, null);
    }

    public SpecialtyFilter(Integer specialtyCode, String name, Boolean hasSubjects) {
        this(specialtyCode, name, hasSubjects, null, null, null, null, null, null);
    }

    public SpecialtyFilter(Integer specialtyCode, String name, Boolean hasSubjects, String commissionCourseCode,
            Long commissionAcademicPeriodId) {
        this(specialtyCode, name, hasSubjects, commissionCourseCode, commissionAcademicPeriodId, null, null, null, null);
    }
}
