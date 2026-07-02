package ar.edu.utn.frc.siga.common.exception;

import lombok.extern.slf4j.Slf4j;
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

    @ExceptionHandler(SigaAppException.class)
    public ProblemDetail handleAppException(SigaAppException ex) {
        log.warn("{}: {}", ex.getTitle(), ex.getMessage());
        ProblemDetail problem = ProblemDetail.forStatus(ex.getStatus());
        problem.setTitle(ex.getTitle());
        problem.setDetail(ex.getMessage());
        return problem;
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail handleValidation(MethodArgumentNotValidException ex) {
        Map<String, List<String>> fieldErrors = ex.getBindingResult().getAllErrors().stream()
                .collect(Collectors.groupingBy(
                        error -> error instanceof FieldError fe ? fe.getField() : error.getObjectName(),
                        Collectors.mapping(error -> error.getDefaultMessage(), Collectors.toList())
                ));

        log.warn("Validation failed: {}", fieldErrors);
        ProblemDetail problem = ProblemDetail.forStatus(org.springframework.http.HttpStatus.BAD_REQUEST);
        problem.setTitle("Validation failed");
        problem.setProperty("errors", fieldErrors);
        return problem;
    }
}
