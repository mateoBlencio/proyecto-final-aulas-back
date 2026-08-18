package ar.edu.utn.frc.siga.common.exception;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.support.DefaultMessageSourceResolvable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.ErrorResponse;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(SigaAppException.class)
    public ProblemDetail handleAppException(SigaAppException ex) {
        log.warn("{}: {}", ex.getTitle(), ex.getMessage());
        ProblemDetail problem = ProblemDetails.of(ex.getStatus(), ex.getTitle(), ex.getMessage());
        ex.getProperties().forEach(problem::setProperty);
        return problem;
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail handleValidation(MethodArgumentNotValidException ex) {
        Map<String, List<String>> fieldErrors = ex.getBindingResult().getAllErrors().stream()
                .collect(Collectors.groupingBy(
                        error -> error instanceof FieldError fe ? fe.getField() : error.getObjectName(),
                        Collectors.mapping(DefaultMessageSourceResolvable::getDefaultMessage, Collectors.toList())
                ));

        log.warn("Validación fallida: {}", fieldErrors);
        ProblemDetail problem = ProblemDetails.of(HttpStatus.BAD_REQUEST, "Validación fallida", null);
        problem.setProperty("errors", fieldErrors);
        return problem;
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ProblemDetail handleConstraintViolation(ConstraintViolationException ex) {
        Map<String, List<String>> errors = ex.getConstraintViolations().stream()
                .collect(Collectors.groupingBy(
                        this::propertyName,
                        Collectors.mapping(ConstraintViolation::getMessage, Collectors.toList())
                ));

        log.warn("Parámetros inválidos: {}", errors);
        ProblemDetail problem = ProblemDetails.of(HttpStatus.BAD_REQUEST, "Parámetros inválidos",
                "Uno o más parámetros no cumplen las restricciones de validación.");
        problem.setProperty("errors", errors);
        return problem;
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ProblemDetail handleNotReadable(HttpMessageNotReadableException ex) {
        log.warn("Cuerpo de la petición ilegible: {}", ex.getMessage());
        return ProblemDetails.of(HttpStatus.BAD_REQUEST, "Cuerpo de la petición ilegible",
                "El cuerpo de la petición no pudo ser interpretado. Verifique el formato enviado.");
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ProblemDetail handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        log.warn("Parámetro con tipo inválido: {}", ex.getMessage());
        String expectedType = ex.getRequiredType() != null ? ex.getRequiredType().getSimpleName() : "desconocido";
        return ProblemDetails.of(HttpStatus.BAD_REQUEST, "Parámetro con tipo inválido",
                "El parámetro '" + ex.getName() + "' debe ser de tipo " + expectedType + ".");
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ProblemDetail handleMaxUploadSize(MaxUploadSizeExceededException ex) {
        log.warn("Archivo demasiado grande: {}", ex.getMessage());
        return ProblemDetails.of(HttpStatus.CONTENT_TOO_LARGE, "Archivo demasiado grande",
                "El archivo enviado supera el tamaño máximo permitido.");
    }

    @ExceptionHandler(AccessDeniedException.class)
    public void handleAccessDenied(AccessDeniedException ex) throws AccessDeniedException {
        throw ex;
    }

    @ExceptionHandler(Exception.class)
    public ProblemDetail handleUnexpected(Exception ex) {
        if (ex instanceof ErrorResponse errorResponse) {
            log.warn("Excepción de framework: {}", ex.getMessage());
            return errorResponse.getBody();
        }
        log.error("Error no controlado", ex);
        return ProblemDetails.of(HttpStatus.INTERNAL_SERVER_ERROR, "Error interno", "Error interno del servidor.");
    }

    private String propertyName(ConstraintViolation<?> violation) {
        return violation.getPropertyPath().toString();
    }
}
