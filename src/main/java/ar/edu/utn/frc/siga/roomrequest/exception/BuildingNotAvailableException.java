package ar.edu.utn.frc.siga.roomrequest.exception;

import ar.edu.utn.frc.siga.common.exception.SigaAppException;
import org.springframework.http.HttpStatus;

import java.io.Serial;

public class BuildingNotAvailableException extends SigaAppException {

    @Serial
    private static final long serialVersionUID = 1L;

    public BuildingNotAvailableException(Long buildingId) {
        super(HttpStatus.BAD_REQUEST, "Building not available",
                "El edificio no tiene ninguna aula libre que cumpla los requisitos del pedido.");
        withProperty("buildingId", buildingId);
    }
}
