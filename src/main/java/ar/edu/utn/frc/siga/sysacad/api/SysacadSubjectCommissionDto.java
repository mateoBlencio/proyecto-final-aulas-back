package ar.edu.utn.frc.siga.sysacad.api;

public record SysacadSubjectCommissionDto(
        String courseCode,
        Integer specialtyCode,
        Integer studyPlanCode,
        Integer subjectCode,
        Integer enrolledCount,
        Integer semester
) {
    public SysacadSubjectCommissionDto withSemester(Integer semester) {
        return new SysacadSubjectCommissionDto(courseCode, specialtyCode, studyPlanCode, subjectCode,
                enrolledCount, semester);
    }
}
