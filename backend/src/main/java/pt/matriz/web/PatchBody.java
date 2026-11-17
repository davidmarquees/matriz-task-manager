package pt.matriz.web;

import java.util.Iterator;
import java.util.Set;

import org.springframework.http.HttpStatus;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * Reads a partial update (PATCH) body, where a missing field means "unchanged" and an explicit null
 * means "clear". Applies the contract rules: unknown field → 400; wrong type → 422 INVALID_VALUE;
 * `version` mandatory; at least one field besides `version`.
 */
public final class PatchBody {

    private final ObjectNode body;

    private PatchBody(ObjectNode body) {
        this.body = body;
    }

    public static PatchBody of(JsonNode node, Set<String> allowedFields) {
        if (node == null || !node.isObject()) {
            throw new ApiException400();
        }
        Iterator<String> names = node.fieldNames();
        while (names.hasNext()) {
            if (!allowedFields.contains(names.next())) {
                throw new ApiException400();
            }
        }
        PatchBody patch = new PatchBody((ObjectNode) node);
        patch.version(); // mandatory
        if (node.size() < 2) {
            throw ApiException.invalid("body", "REQUIRED");
        }
        return patch;
    }

    public int version() {
        JsonNode value = body.get("version");
        if (value == null || value.isNull()) {
            throw ApiException.invalid("version", "REQUIRED");
        }
        if (!value.isInt() || value.intValue() < 0) {
            throw ApiException.invalid("version", "INVALID_VALUE");
        }
        return value.intValue();
    }

    public boolean has(String field) {
        return body.has(field);
    }

    /** Raw JSON value of a field (null when absent). */
    public JsonNode node(String field) {
        return body.get(field);
    }

    /** Text value of a present field; null when sent as null; 422 INVALID_VALUE for another type. */
    public String text(String field) {
        JsonNode value = body.get(field);
        if (value == null || value.isNull()) {
            return null;
        }
        if (!value.isTextual()) {
            throw ApiException.invalid(field, "INVALID_VALUE");
        }
        return value.textValue();
    }

    /** Thrown for a malformed body; handled as 400 by {@link ApiExceptionHandler}. */
    public static final class ApiException400 extends RuntimeException {
        public ApiException400() {
            super(HttpStatus.BAD_REQUEST.getReasonPhrase());
        }
    }
}
