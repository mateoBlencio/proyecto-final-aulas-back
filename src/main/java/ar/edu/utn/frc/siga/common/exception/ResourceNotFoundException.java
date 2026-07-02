package ar.edu.utn.frc.siga.common.exception;

import org.springframework.http.HttpStatus;

public class ResourceNotFoundException extends SigaAppException {

    public ResourceNotFoundException(String message) {
        super(HttpStatus.NOT_FOUND, "Resource not found", message);
    }
}
