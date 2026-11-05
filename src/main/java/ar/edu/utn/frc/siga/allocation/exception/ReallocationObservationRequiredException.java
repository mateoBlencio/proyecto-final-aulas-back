package ar.edu.utn.frc.siga.allocation.exception;

import ar.edu.utn.frc.siga.common.exception.SigaAppException;
import lombok.Getter;
import org.springframework.http.HttpStatus;
import org.springframework.modulith.NamedInterface;

import java.io.Serial;
import java.util.List;

@Getter
@NamedInterface("api")
public final class ReallocationObservationRequiredException extends SigaAppException {

    @Serial
    private static final long serialVersionUID = 1L;

    // El front abre el diálogo de motivo con este título y la lista vacía, igual que con un choque tolerado.
    public ReallocationObservationRequiredException() {
        super(HttpStatus.BAD_REQUEST, "Observation required",
                "Cambiar el aula de una clase que ya tiene una asignada exige indicar un motivo.");
        withProperty("overlaps", List.of());
        withProperty("classroomIds", List.of());
    }
}
