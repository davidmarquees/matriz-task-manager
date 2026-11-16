package pt.matriz.web;

import java.util.List;

import org.springframework.http.HttpStatus;

/**
 * Business error with an HTTP status from the contract (openapi.yaml, docs/database.md section 6).
 * Turned into a Problem Details response by {@link ApiExceptionHandler}; never carries internal details.
 */
public class ApiException extends RuntimeException {

    private final HttpStatus status;
    private final String code;
    private final List<FieldError> errors;
    private final String detail;
    private final Long retryAfterSeconds;

    private ApiException(HttpStatus status, String code, List<FieldError> errors) {
        this(status, code, errors, null, null);
    }

    private ApiException(HttpStatus status, String code, List<FieldError> errors, String detail,
                         Long retryAfterSeconds) {
        super(status.getReasonPhrase());
        this.status = status;
        this.code = code;
        this.errors = errors;
        this.detail = detail;
        this.retryAfterSeconds = retryAfterSeconds;
    }

    /** 401 at login: the same answer for every failure (D6, D7). */
    public static ApiException loginFailed() {
        return new ApiException(HttpStatus.UNAUTHORIZED, null, List.of(), "Invalid email or password.", null);
    }

    /** 429: too many login attempts from one IP address. */
    public static ApiException tooManyRequests(long retryAfterSeconds) {
        return new ApiException(HttpStatus.TOO_MANY_REQUESTS, null, List.of(), null, retryAfterSeconds);
    }

    /** 404: the resource does not exist or the user is not a member of its project (E4). */
    public static ApiException notFound() {
        return new ApiException(HttpStatus.NOT_FOUND, null, List.of());
    }

    /** 403: authenticated but not allowed. */
    public static ApiException forbidden() {
        return new ApiException(HttpStatus.FORBIDDEN, null, List.of());
    }

    /** 409 with a machine-readable reason (VERSION_CONFLICT, ALREADY_MEMBER...). */
    public static ApiException conflict(String code) {
        return new ApiException(HttpStatus.CONFLICT, code, List.of());
    }

    /** 422 for one field that breaks a rule. */
    public static ApiException invalid(String field, String code) {
        return new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, null, List.of(new FieldError(field, code)));
    }

    public HttpStatus status() {
        return status;
    }

    public String code() {
        return code;
    }

    public List<FieldError> errors() {
        return errors;
    }

    public String detail() {
        return detail;
    }

    public Long retryAfterSeconds() {
        return retryAfterSeconds;
    }

    /** One entry of the `errors` list of a 422 response. */
    public record FieldError(String field, String code) {
    }
}
