package ar.edu.utn.frc.siga.audit.repository;

/** Scopes a drill-down to an operation or an Envers revision; exactly one is non-null. */
public record ChangeScope(String operationId, Integer revision) {

    public static ChangeScope ofOperation(String operationId) {
        return new ChangeScope(operationId, null);
    }

    public static ChangeScope ofRevision(int revision) {
        return new ChangeScope(null, revision);
    }
}
