package pt.matriz.web;

import java.io.IOException;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;

import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.servlet.http.HttpServletResponse;

/** Builds Problem Details (RFC 9457) bodies as described in openapi.yaml, without internal details (NFR5). */
public final class Problems {

    private Problems() {
    }

    public static ProblemDetail of(HttpStatus status) {
        ProblemDetail problem = ProblemDetail.forStatus(status);
        // RFC 9110 name for 422 (the contract uses it); other statuses keep the standard reason phrase.
        problem.setTitle(status == HttpStatus.UNPROCESSABLE_ENTITY ? "Unprocessable Content" : status.getReasonPhrase());
        return problem;
    }

    public static ProblemDetail of(HttpStatus status, String code, List<ApiException.FieldError> errors) {
        ProblemDetail problem = of(status);
        if (code != null) {
            problem.setProperty("code", code);
        }
        if (status == HttpStatus.UNPROCESSABLE_ENTITY) {
            problem.setProperty("errors", errors);
        }
        return problem;
    }

    /** Writes a problem from a servlet filter or a security handler, outside Spring MVC. */
    public static void write(HttpServletResponse response, ObjectMapper json, ProblemDetail problem) throws IOException {
        response.setStatus(problem.getStatus());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        json.writeValue(response.getOutputStream(), problem);
    }
}
