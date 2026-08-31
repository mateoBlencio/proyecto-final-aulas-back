package ar.edu.utn.frc.siga.roomrequest.model;

/**
 * Tipo de solicitud de aula. Cada tipo activa, oculta o restringe distintos campos del formulario;
 * esa "matriz de campos por tipo" la resuelve el handler correspondiente ({@code roomrequest.handler}),
 * que es la única fuente de verdad de las reglas de cada tipo.
 *
 * <p>{@code PARTIAL_EXAM} vive como dos valores —en horario de clases / fuera de horario— igual que
 * el cambio de aula, que ya es {@code ONE_TIME_ROOM_CHANGE} / {@code REGULAR_ROOM_CHANGE}.
 */
public enum RoomRequestType {

    ONE_TIME_ROOM_CHANGE,
    REGULAR_ROOM_CHANGE,
    PARTIAL_EXAM_IN_CLASS,
    PARTIAL_EXAM_OFF_SCHEDULE,
    FINAL_EXAM,
    CONFERENCE,
    OTHER
}
