package ar.edu.utn.frc.siga.roomrequest.exception;

import ar.edu.utn.frc.siga.common.exception.SigaAppException;
import ar.edu.utn.frc.siga.roomrequest.dto.response.OriginalConflictDto;
import org.springframework.http.HttpStatus;

import java.io.Serial;
import java.util.List;

public class RoomRequestOriginalOccupiedException extends SigaAppException {

    @Serial
    private static final long serialVersionUID = 1L;

    public RoomRequestOriginalOccupiedException(Long itemId, List<OriginalConflictDto> conflicts) {
        super(HttpStatus.CONFLICT, "Original classroom occupied",
                "No se puede cancelar: el aula original está ocupada en " + conflicts.size()
                        + (conflicts.size() == 1 ? " fecha." : " fechas."));
        withProperty("code", "ORIGINAL_CLASSROOM_OCCUPIED");
        withProperty("itemId", itemId);
        withProperty("conflicts", conflicts);
    }
}
