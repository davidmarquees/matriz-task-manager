package pt.matriz.web;

import java.sql.SQLException;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import com.fasterxml.jackson.databind.exc.MismatchedInputException;
import com.fasterxml.jackson.databind.exc.UnrecognizedPropertyException;

/**
 * Turns every error into Problem Details with the status codes of openapi.yaml (NFR5):
 * 400 malformed request, unknown field or a NUL character in the input; 422 rule broken, wrong type or null;
 * 500 generic.
 * Standard Spring MVC errors (415, 405, 404 for unknown paths) are handled by the base class.
 */
@RestControllerAdvice
public class ApiExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(ApiException.class)
    ResponseEntity<ProblemDetail> handleApi(ApiException ex) {
        ProblemDetail detail = Problems.of(ex.status(), ex.code(), ex.errors());
        detail.setDetail(ex.detail());
        ResponseEntity.BodyBuilder response = ResponseEntity.status(ex.status())
                .contentType(MediaType.APPLICATION_PROBLEM_JSON);
        if (ex.retryAfterSeconds() != null) {
            response.header(HttpHeaders.RETRY_AFTER, String.valueOf(ex.retryAfterSeconds()));
        }
        return response.body(detail);
    }

    @ExceptionHandler(PatchBody.ApiException400.class)
    ResponseEntity<ProblemDetail> handleMalformedPatch(PatchBody.ApiException400 ex) {
        return problem(Problems.of(HttpStatus.BAD_REQUEST));
    }

    /**
     * PostgreSQL cannot store the NUL character (U+0000) and refuses it with SQLSTATE 22021. Whatever the field
     * or parameter (body, query string, login, search), that is a malformed request (400), not a server error
     * (docs/quality-security-review.md, Q-6). The transaction is rolled back, so nothing is saved.
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    ResponseEntity<ProblemDetail> handleDataIntegrity(DataIntegrityViolationException ex) {
        if (isNulCharacter(ex)) {
            return problem(Problems.of(HttpStatus.BAD_REQUEST));
        }
        return handleUnexpected(ex);
    }

    private static boolean isNulCharacter(Throwable ex) {
        for (Throwable cause = ex; cause != null; cause = cause.getCause()) {
            if (cause instanceof SQLException sql && "22021".equals(sql.getSQLState())) {
                return true;
            }
        }
        return false;
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ProblemDetail> handleUnexpected(Exception ex) {
        // Details only in the server log; the client gets a generic message (D28).
        log.error("Unexpected error", ex);
        return problem(Problems.of(HttpStatus.INTERNAL_SERVER_ERROR));
    }

    @Override
    protected ResponseEntity<Object> handleHttpMessageNotReadable(HttpMessageNotReadableException ex,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        Throwable cause = ex.getCause();
        if (cause instanceof MismatchedInputException mismatch && !(cause instanceof UnrecognizedPropertyException)) {
            // Well-formed JSON with a wrong type: 422 for the field
            String field = mismatch.getPath().isEmpty() ? "body"
                    : String.valueOf(mismatch.getPath().get(mismatch.getPath().size() - 1).getFieldName());
            return body(Problems.of(HttpStatus.UNPROCESSABLE_ENTITY, null,
                    List.of(new ApiException.FieldError(field, "INVALID_VALUE"))));
        }
        return body(Problems.of(HttpStatus.BAD_REQUEST));
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        List<ApiException.FieldError> errors = ex.getBindingResult().getFieldErrors().stream()
                .map(e -> new ApiException.FieldError(e.getField(),
                        e.getRejectedValue() == null ? "REQUIRED" : codeOf(e.getCode())))
                .distinct()
                .toList();
        return body(Problems.of(HttpStatus.UNPROCESSABLE_ENTITY, null, errors));
    }

    @Override
    protected ResponseEntity<Object> createResponseEntity(Object body, HttpHeaders headers,
            HttpStatusCode statusCode, WebRequest request) {
        // Standard errors from the base class: same title format, no request path or internal details.
        if (body instanceof ProblemDetail detail) {
            detail.setDetail(null);
            detail.setInstance(null);
        }
        return super.createResponseEntity(body, headers, statusCode, request);
    }

    /** Bean Validation annotation → contract error code (ValidationProblem.errors[].code). */
    private static String codeOf(String constraint) {
        if (constraint == null) {
            return "INVALID_VALUE";
        }
        return switch (constraint) {
            case "NotNull", "NotEmpty" -> "REQUIRED";
            case "NotBlank" -> "BLANK";
            case "Size", "Length" -> "TOO_LONG"; // minimum lengths are checked in the services (TOO_SHORT)
            default -> "INVALID_VALUE";
        };
    }

    private static ResponseEntity<ProblemDetail> problem(ProblemDetail detail) {
        return ResponseEntity.status(detail.getStatus()).contentType(MediaType.APPLICATION_PROBLEM_JSON).body(detail);
    }

    private static ResponseEntity<Object> body(ProblemDetail detail) {
        return ResponseEntity.status(detail.getStatus()).contentType(MediaType.APPLICATION_PROBLEM_JSON).body(detail);
    }
}
