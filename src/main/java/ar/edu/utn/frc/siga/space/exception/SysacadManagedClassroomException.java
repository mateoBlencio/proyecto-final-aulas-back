package ar.edu.utn.frc.siga.space.exception;

import ar.edu.utn.frc.siga.common.exception.SigaAppException;
import org.springframework.http.HttpStatus;

import java.io.Serial;
import java.util.List;

public class SysacadManagedClassroomException extends SigaAppException {

    @Serial
    private static final long serialVersionUID = 1L;

    public SysacadManagedClassroomException(Long classroomId, List<String> fields) {
        super(HttpStatus.CONFLICT, "Aula sincronizada con SysAcad",
                "El aula " + classroomId + " viene de SysAcad: no se puede modificar " + String.join(", ", fields) + ".");
    }
}
