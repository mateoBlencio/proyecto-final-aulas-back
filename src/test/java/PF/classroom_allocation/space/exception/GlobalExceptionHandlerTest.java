package PF.classroom_allocation.space.exception;

import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import static org.junit.jupiter.api.Assertions.*;

class GlobalExceptionHandlerTest {

    private GlobalExceptionHandler handler;

    @BeforeEach
    void setUp() {
        handler = new GlobalExceptionHandler();
    }

    @Test
    void handleNotFound_shouldReturn404WithBody() {
        var ex = new ResourceNotFoundException("Classroom not found");

        ResponseEntity<Map<String, Object>> response = handler.handleNotFound(ex);

        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        Map<String, Object> body = response.getBody();
        assertNotNull(body);
        assertEquals(404, body.get("status"));
        assertEquals("Classroom not found", body.get("error"));
        assertNotNull(body.get("timestamp"));
    }

    @Test
    void handleDomainException_shouldReturn400WithBody() {
        var ex = new SpaceDomainException("Floor exceeds building");

        ResponseEntity<Map<String, Object>> response = handler.handleDomainException(ex);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        Map<String, Object> body = response.getBody();
        assertNotNull(body);
        assertEquals(400, body.get("status"));
        assertEquals("Floor exceeds building", body.get("error"));
        assertNotNull(body.get("timestamp"));
    }

    @Test
    void handleValidation_shouldReturn400WithFieldErrors() {
        var target = new Object();
        var bindingResult = new BeanPropertyBindingResult(target, "target");
        bindingResult.addError(new FieldError("target", "roomNumber", "must not be blank"));
        bindingResult.addError(new FieldError("target", "capacity", "must be positive"));
        var ex = new MethodArgumentNotValidException(null, bindingResult);

        ResponseEntity<Map<String, Object>> response = handler.handleValidation(ex);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        Map<String, Object> body = response.getBody();
        assertNotNull(body);
        assertEquals(400, body.get("status"));
        String error = (String) body.get("error");
        assertTrue(error.contains("roomNumber"));
        assertTrue(error.contains("capacity"));
    }

    @Test
    void handleValidation_shouldReturnDefaultMessageWhenNoErrors() {
        var target = new Object();
        var bindingResult = new BeanPropertyBindingResult(target, "target");
        var ex = new MethodArgumentNotValidException(null, bindingResult);

        ResponseEntity<Map<String, Object>> response = handler.handleValidation(ex);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        Map<String, Object> body = response.getBody();
        assertNotNull(body);
        assertEquals("Validation failed", body.get("error"));
    }
}
