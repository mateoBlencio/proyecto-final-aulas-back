package ar.edu.utn.frc.siga.common.exception;

import ar.edu.utn.frc.siga.common.exception.ResourceNotFoundException;
import ar.edu.utn.frc.siga.space.exception.SpaceDomainException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.ProblemDetail;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GlobalExceptionHandlerTest {

    private GlobalExceptionHandler handler;

    @BeforeEach
    void setUp() {
        handler = new GlobalExceptionHandler();
    }

    @Test
    void handleNotFound_shouldReturn404WithBody() {
        var ex = new ResourceNotFoundException("Classroom not found");

        ProblemDetail result = handler.handleAppException(ex);

        assertEquals(404, result.getStatus());
        assertEquals("Resource not found", result.getTitle());
        assertEquals("Classroom not found", result.getDetail());
    }

    @Test
    void handleDomainException_shouldReturn400WithBody() {
        var ex = new SpaceDomainException("Floor exceeds building");

        ProblemDetail result = handler.handleAppException(ex);

        assertEquals(400, result.getStatus());
        assertEquals("Space domain error", result.getTitle());
        assertEquals("Floor exceeds building", result.getDetail());
    }

    @SuppressWarnings("unchecked")
    @Test
    void handleValidation_shouldReturn400WithFieldErrors() {
        var target = new Object();
        var bindingResult = new BeanPropertyBindingResult(target, "target");
        bindingResult.addError(new FieldError("target", "roomNumber", "must not be blank"));
        bindingResult.addError(new FieldError("target", "capacity", "must be positive"));
        var ex = new MethodArgumentNotValidException(null, bindingResult);

        ProblemDetail result = handler.handleValidation(ex);

        assertEquals(400, result.getStatus());
        assertEquals("Validation failed", result.getTitle());

        Map<String, List<String>> errors = (Map<String, List<String>>) result.getProperties().get("errors");
        assertNotNull(errors);
        assertTrue(errors.containsKey("roomNumber"));
        assertTrue(errors.containsKey("capacity"));
        assertEquals("must not be blank", errors.get("roomNumber").get(0));
        assertEquals("must be positive", errors.get("capacity").get(0));
    }

    @Test
    void handleValidation_shouldReturnDefaultMessageWhenNoErrors() {
        var target = new Object();
        var bindingResult = new BeanPropertyBindingResult(target, "target");
        var ex = new MethodArgumentNotValidException(null, bindingResult);

        ProblemDetail result = handler.handleValidation(ex);

        assertEquals(400, result.getStatus());
        assertEquals("Validation failed", result.getTitle());
    }
}
