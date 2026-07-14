package ar.edu.utn.frc.siga.common.exception;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
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

/**
 * Único punto de traducción de excepciones a respuestas HTTP ({@link ProblemDetail}).
 * Todos los módulos deben lanzar subclases de {@link SigaAppException} en lugar de
 * definir sus propios {@code @RestControllerAdvice}.
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * Traduce cualquier {@link SigaAppException} de dominio a un {@code ProblemDetail} con el
     * status, título y detalle propios de la excepción, más sus propiedades extra.
     */
    @ExceptionHandler(SigaAppException.class)
    public ProblemDetail handleAppException(SigaAppException ex) {
        log.warn("{}: {}", ex.getTitle(), ex.getMessage());
        ProblemDetail problem = ProblemDetail.forStatus(ex.getStatus());
        problem.setTitle(ex.getTitle());
        problem.setDetail(ex.getMessage());
        ex.getProperties().forEach(problem::setProperty);
        return problem;
    }

    /**
     * Captura fallos de validación de {@code @Valid} en el body de la request y devuelve
     * un 400 con los errores agrupados por campo (o por objeto, si no hay campo asociado).
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail handleValidation(MethodArgumentNotValidException ex) {
        Map<String, List<String>> fieldErrors = ex.getBindingResult().getAllErrors().stream()
                .collect(Collectors.groupingBy(
                        error -> error instanceof FieldError fe ? fe.getField() : error.getObjectName(),
                        Collectors.mapping(error -> error.getDefaultMessage(), Collectors.toList())
                ));

        log.warn("Validación fallida: {}", fieldErrors);
        ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.BAD_REQUEST);
        problem.setTitle("Validación fallida");
        problem.setProperty("errors", fieldErrors);
        return problem;
    }

    /**
     * Captura violaciones de restricciones de validación en parámetros/path variables (no en
     * el body) y devuelve un 400 con los errores agrupados por nombre de propiedad.
     */
    @ExceptionHandler(ConstraintViolationException.class)
    public ProblemDetail handleConstraintViolation(ConstraintViolationException ex) {
        Map<String, List<String>> errors = ex.getConstraintViolations().stream()
                .collect(Collectors.groupingBy(
                        this::propertyName,
                        Collectors.mapping(ConstraintViolation::getMessage, Collectors.toList())
                ));

        log.warn("Parámetros inválidos: {}", errors);
        ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.BAD_REQUEST);
        problem.setTitle("Parámetros inválidos");
        problem.setDetail("Uno o más parámetros no cumplen las restricciones de validación.");
        problem.setProperty("errors", errors);
        return problem;
    }

    /**
     * Captura errores de deserialización del body (JSON malformado, tipo incompatible, etc.)
     * y devuelve un 400 genérico sin filtrar el detalle interno del parser.
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ProblemDetail handleNotReadable(HttpMessageNotReadableException ex) {
        log.warn("Cuerpo de la petición ilegible: {}", ex.getMessage());
        ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.BAD_REQUEST);
        problem.setTitle("Cuerpo de la petición ilegible");
        problem.setDetail("El cuerpo de la petición no pudo ser interpretado. Verifique el formato enviado.");
        return problem;
    }

    /**
     * Captura un parámetro que no puede convertirse al tipo esperado (por ejemplo, un
     * {@code id} no numérico en la URL) y devuelve un 400 indicando el tipo requerido.
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ProblemDetail handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        log.warn("Parámetro con tipo inválido: {}", ex.getMessage());
        ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.BAD_REQUEST);
        problem.setTitle("Parámetro con tipo inválido");
        String expectedType = ex.getRequiredType() != null ? ex.getRequiredType().getSimpleName() : "desconocido";
        problem.setDetail("El parámetro '" + ex.getName() + "' debe ser de tipo " + expectedType + ".");
        return problem;
    }

    /**
     * Captura un archivo subido (por ejemplo, en la importación de Excel) que excede el
     * tamaño máximo configurado y devuelve un 413 (Content Too Large).
     */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ProblemDetail handleMaxUploadSize(MaxUploadSizeExceededException ex) {
        log.warn("Archivo demasiado grande: {}", ex.getMessage());
        ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.CONTENT_TOO_LARGE);
        problem.setTitle("Archivo demasiado grande");
        problem.setDetail("El archivo enviado supera el tamaño máximo permitido.");
        return problem;
    }

    /**
     * Fallback para cualquier excepción no manejada explícitamente. Si el framework ya generó
     * un {@link ErrorResponse} con su propio {@code ProblemDetail} (404, 405, etc.), lo respeta
     * tal cual; de lo contrario, la trata como error inesperado y devuelve un 500 genérico
     * sin exponer el detalle interno.
     */
    /**
     * Deja pasar las excepciones de autorización de Spring Security en vez de que el fallback
     * las capture como 500. Al relanzarlas, la cadena de filtros ({@code ExceptionTranslationFilter}
     * → {@code RestAccessDeniedHandler}) las traduce a 403. Sin esto, un {@code @PreAuthorize}
     * denegado a nivel de método devolvería 500.
     */
    @ExceptionHandler(AccessDeniedException.class)
    public void handleAccessDenied(AccessDeniedException ex) throws AccessDeniedException {
        throw ex;
    }

    @ExceptionHandler(Exception.class)
    public ProblemDetail handleUnexpected(Exception ex) {
        // Las excepciones de framework que ya traen su ProblemDetail (404 de recurso
        // inexistente, 405 método no soportado, etc.) conservan su status original.
        if (ex instanceof ErrorResponse errorResponse) {
            log.warn("Excepción de framework: {}", ex.getMessage());
            return errorResponse.getBody();
        }
        log.error("Error no controlado", ex);
        ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.INTERNAL_SERVER_ERROR);
        problem.setTitle("Error interno");
        problem.setDetail("Error interno del servidor.");
        return problem;
    }

    private String propertyName(ConstraintViolation<?> violation) {
        return violation.getPropertyPath().toString();
    }
}
