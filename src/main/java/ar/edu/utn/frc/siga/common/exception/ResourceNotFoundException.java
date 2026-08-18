package ar.edu.utn.frc.siga.common.exception;

import org.springframework.http.HttpStatus;

import java.io.Serial;

public class ResourceNotFoundException extends SigaAppException {

    @Serial
    private static final long serialVersionUID = 1L;

    public ResourceNotFoundException(String message) {
        super(HttpStatus.NOT_FOUND, "Resource not found", message);
    }

    public static ResourceNotFoundException of(String resource, Object id) {
        return new ResourceNotFoundException(resource + " not found with id: " + id);
    }
}