package ar.edu.utn.frc.siga.preview.dto.response;

public record UnmetRequirementDto(RequirementType type, RequirementSeverity severity) {

    public enum RequirementType {
        PROJECTOR, COMPUTERS, SOFTWARE, EXAM_USERS
    }

    public enum RequirementSeverity {
        MINOR, SEVERE, UNVERIFIABLE
    }
}
