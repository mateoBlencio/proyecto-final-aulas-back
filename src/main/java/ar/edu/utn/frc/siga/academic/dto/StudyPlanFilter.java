package ar.edu.utn.frc.siga.academic.dto;

public record StudyPlanFilter(
        Integer planCode,
        Integer specialtyCode,
        String q
) {
    public StudyPlanFilter(Integer planCode, Integer specialtyCode) {
        this(planCode, specialtyCode, null);
    }
}
