package ar.edu.utn.frc.siga.allocation.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Expone la lista de conflictos de {@link ReassignConflictException} como propiedad
 * del ProblemDetail. Vive en allocation para no acoplar el handler global (common).
 */
@Slf4j
@RestControllerAdvice
public class ReassignConflictExceptionHandler {

    @ExceptionHandler(ReassignConflictException.class)
    public ProblemDetail handleReassignConflict(ReassignConflictException ex) {
        log.warn("{}: {}", ex.getTitle(), ex.getMessage());
        ProblemDetail problem = ProblemDetail.forStatus(ex.getStatus());
        problem.setTitle(ex.getTitle());
        problem.setDetail(ex.getMessage());
        problem.setProperty("conflicts", ex.getConflicts());
        return problem;
    }
}
