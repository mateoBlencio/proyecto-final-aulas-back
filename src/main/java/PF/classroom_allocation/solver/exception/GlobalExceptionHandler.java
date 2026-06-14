package PF.classroom_allocation.solver.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail handleValidation(MethodArgumentNotValidException ex) {
        Map<String, List<String>> fieldErrors = ex.getBindingResult().getAllErrors().stream()
                .collect(Collectors.groupingBy(
                        error -> error instanceof FieldError fe ? fe.getField() : error.getObjectName(),
                        Collectors.mapping(error -> error.getDefaultMessage(), Collectors.toList())
                ));

        log.warn("Validation failed: {}", fieldErrors);
        ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.BAD_REQUEST);
        problem.setTitle("Validation failed");
        problem.setProperty("errors", fieldErrors);
        return problem;
    }

    @ExceptionHandler(InvalidAllocationRequestException.class)
    public ProblemDetail handleInvalidRequest(InvalidAllocationRequestException ex) {
        log.warn("Invalid allocation request: {}", ex.getMessage());
        ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.BAD_REQUEST);
        problem.setTitle("Invalid allocation request");
        problem.setDetail(ex.getMessage());
        return problem;
    }

    @ExceptionHandler(InfeasibleScheduleException.class)
    public ProblemDetail handleInfeasible(InfeasibleScheduleException ex) {
        log.warn("Infeasible schedule: {}", ex.getMessage());
        ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.UNPROCESSABLE_ENTITY);
        problem.setTitle("Infeasible schedule");
        problem.setDetail(ex.getMessage());
        return problem;
    }

    @ExceptionHandler(SchedulingException.class)
    public ProblemDetail handleScheduling(SchedulingException ex) {
        log.error("Solver error", ex);
        ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.INTERNAL_SERVER_ERROR);
        problem.setTitle("Solver error");
        problem.setDetail(ex.getMessage());
        return problem;
    }
}