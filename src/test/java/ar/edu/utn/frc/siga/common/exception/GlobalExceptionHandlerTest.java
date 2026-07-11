package ar.edu.utn.frc.siga.common.exception;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.http.HttpInputMessage;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.server.ResponseStatusException;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Invocación directa del {@link GlobalExceptionHandler}, sin levantar contexto MVC.
 */
@DisplayName("GlobalExceptionHandler")
class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    // ---------- SigaAppException conocidas de common ----------

    @Test
    @DisplayName("ResourceNotFoundException se traduce a ProblemDetail 404")
    void resourceNotFoundExceptionMapsTo404() {
        ProblemDetail problem = handler.handleAppException(ResourceNotFoundException.of("Subject", 5L));

        assertThat(problem.getStatus()).isEqualTo(404);
        assertThat(problem.getTitle()).isEqualTo("Resource not found");
        assertThat(problem.getDetail()).isEqualTo("Subject not found with id: 5");
    }

    @Test
    @DisplayName("InvalidDateRangeException se traduce a ProblemDetail 400")
    void invalidDateRangeExceptionMapsTo400() {
        ProblemDetail problem = handler.handleAppException(new InvalidDateRangeException("fin antes que inicio"));

        assertThat(problem.getStatus()).isEqualTo(400);
        assertThat(problem.getTitle()).isEqualTo("Invalid date range");
        assertThat(problem.getDetail()).isEqualTo("fin antes que inicio");
    }

    @Test
    @DisplayName("el handler es genérico: usa el status/title propios de cada subtipo de SigaAppException, no un mapeo hardcodeado")
    void handlerIsGenericAcrossStatuses() {
        // Simula los subtipos de otros módulos (409/422/410/500 según docs/modulos/common.md)
        // sin depender de ellos: el contrato es que SigaAppException lleve su propio status.
        for (HttpStatus status : new HttpStatus[]{HttpStatus.CONFLICT, HttpStatus.UNPROCESSABLE_CONTENT,
                HttpStatus.GONE, HttpStatus.INTERNAL_SERVER_ERROR}) {
            ProblemDetail problem = handler.handleAppException(
                    new TestSigaException(status, "titulo-" + status.value(), "detalle-" + status.value()));

            assertThat(problem.getStatus()).isEqualTo(status.value());
            assertThat(problem.getTitle()).isEqualTo("titulo-" + status.value());
            assertThat(problem.getDetail()).isEqualTo("detalle-" + status.value());
        }
    }

    @Test
    @DisplayName("las propiedades extra de la excepción (ej. conflicts) se copian al ProblemDetail")
    void extraPropertiesArePropagatedToProblemDetail() {
        TestSigaException ex = new TestSigaException(HttpStatus.CONFLICT, "Conflicto", "hay solapamiento");
        ex.withProperty("conflicts", Set.of("occ-1", "occ-2"));

        ProblemDetail problem = handler.handleAppException(ex);

        assertThat(problem.getProperties()).containsEntry("conflicts", Set.of("occ-1", "occ-2"));
    }

    // ---------- excepciones de framework ----------

    @Test
    @DisplayName("MethodArgumentNotValidException se traduce a 400 con errores agrupados por campo")
    void methodArgumentNotValidExceptionMapsTo400WithFieldErrors() throws NoSuchMethodException {
        BindingResult bindingResult = new BeanPropertyBindingResult(new Object(), "target");
        bindingResult.rejectValue(null, "obj.invalid", "el objeto es inválido");
        MethodArgumentNotValidException ex = new MethodArgumentNotValidException(dummyMethodParameter(), bindingResult);

        ProblemDetail problem = handler.handleValidation(ex);

        assertThat(problem.getStatus()).isEqualTo(400);
        assertThat(problem.getTitle()).isEqualTo("Validación fallida");
        @SuppressWarnings("unchecked")
        Map<String, Object> errors = (Map<String, Object>) problem.getProperties().get("errors");
        assertThat(errors).containsKey("target");
    }

    @Test
    @DisplayName("ConstraintViolationException se traduce a 400 con errores agrupados por propiedad")
    void constraintViolationExceptionMapsTo400() {
        ConstraintViolation<?> violation = mock(ConstraintViolation.class);
        Path path = mock(Path.class);
        when(path.toString()).thenReturn("year");
        when(violation.getPropertyPath()).thenAnswer(invocation -> path);
        when(violation.getMessage()).thenReturn("debe ser positivo");

        jakarta.validation.ConstraintViolationException ex =
                new jakarta.validation.ConstraintViolationException(Set.of(violation));

        ProblemDetail problem = handler.handleConstraintViolation(ex);

        assertThat(problem.getStatus()).isEqualTo(400);
        assertThat(problem.getTitle()).isEqualTo("Parámetros inválidos");
        @SuppressWarnings("unchecked")
        Map<String, Object> errors = (Map<String, Object>) problem.getProperties().get("errors");
        assertThat(errors).containsKey("year");
    }

    @Test
    @DisplayName("HttpMessageNotReadableException se traduce a 400 con detalle genérico")
    void httpMessageNotReadableExceptionMapsTo400() {
        ProblemDetail problem = handler.handleNotReadable(
                new HttpMessageNotReadableException("JSON roto", mock(HttpInputMessage.class)));

        assertThat(problem.getStatus()).isEqualTo(400);
        assertThat(problem.getTitle()).isEqualTo("Cuerpo de la petición ilegible");
    }

    @Test
    @DisplayName("MethodArgumentTypeMismatchException se traduce a 400 incluyendo el tipo esperado")
    void methodArgumentTypeMismatchExceptionMapsTo400() throws NoSuchMethodException {
        MethodArgumentTypeMismatchException ex = new MethodArgumentTypeMismatchException(
                "abc", Integer.class, "id", dummyMethodParameter(), new NumberFormatException());

        ProblemDetail problem = handler.handleTypeMismatch(ex);

        assertThat(problem.getStatus()).isEqualTo(400);
        assertThat(problem.getDetail()).contains("id").contains("Integer");
    }

    @Test
    @DisplayName("MethodArgumentTypeMismatchException sin tipo requerido conocido usa 'desconocido'")
    void methodArgumentTypeMismatchExceptionWithoutRequiredTypeUsesUnknown() throws NoSuchMethodException {
        MethodArgumentTypeMismatchException ex = new MethodArgumentTypeMismatchException(
                "abc", null, "id", dummyMethodParameter(), new NumberFormatException());

        ProblemDetail problem = handler.handleTypeMismatch(ex);

        assertThat(problem.getDetail()).contains("desconocido");
    }

    @Test
    @DisplayName("MaxUploadSizeExceededException se traduce a 413")
    void maxUploadSizeExceededExceptionMapsTo413() {
        ProblemDetail problem = handler.handleMaxUploadSize(new MaxUploadSizeExceededException(1024L));

        assertThat(problem.getStatus()).isEqualTo(413);
    }

    // ---------- catch-all ----------

    @Test
    @DisplayName("una excepción no controlada se traduce a 500 sin filtrar el mensaje interno al cliente")
    void unexpectedExceptionMapsTo500WithoutLeakingInternalMessage() {
        RuntimeException internal = new RuntimeException("password=supersecreto en la stacktrace");

        ProblemDetail problem = handler.handleUnexpected(internal);

        assertThat(problem.getStatus()).isEqualTo(500);
        assertThat(problem.getTitle()).isEqualTo("Error interno");
        assertThat(problem.getDetail())
                .isEqualTo("Error interno del servidor.")
                .doesNotContain("supersecreto");
    }

    @Test
    @DisplayName("una excepción que ya trae ErrorResponse (ej. 404 de framework) conserva su ProblemDetail original")
    void exceptionImplementingErrorResponsePreservesOriginalProblemDetail() {
        ResponseStatusException ex = new ResponseStatusException(HttpStatus.NOT_FOUND, "ruta no soportada");

        ProblemDetail problem = handler.handleUnexpected(ex);

        assertThat(problem.getStatus()).isEqualTo(404);
    }

    private MethodParameter dummyMethodParameter() throws NoSuchMethodException {
        Method method = getClass().getDeclaredMethod("dummyTarget", String.class);
        return new MethodParameter(method, 0);
    }

    @SuppressWarnings("unused")
    private void dummyTarget(String param) {
        // método de soporte para construir un MethodParameter real en los tests
    }

    /** Subtipo de {@link SigaAppException} de uso exclusivo en tests, para probar la genericidad del handler. */
    private static final class TestSigaException extends SigaAppException {
        private static final long serialVersionUID = 1L;

        TestSigaException(HttpStatus status, String title, String detail) {
            super(status, title, detail);
        }

        @Override
        public TestSigaException withProperty(String key, Object value) {
            super.withProperty(key, value);
            return this;
        }
    }
}
