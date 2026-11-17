package pt.matriz.web;

/**
 * Validation of text fields (A6, A20, docs/database.md): values are trimmed before the length rules
 * are applied; text made only of spaces counts as blank.
 */
public final class TextInput {

    private TextInput() {
    }

    /** Mandatory text: 422 REQUIRED when missing, BLANK when only spaces, TOO_SHORT / TOO_LONG on length. */
    public static String required(String value, String field, int min, int max) {
        if (value == null) {
            throw ApiException.invalid(field, "REQUIRED");
        }
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            throw ApiException.invalid(field, "BLANK");
        }
        if (trimmed.length() < min) {
            throw ApiException.invalid(field, "TOO_SHORT");
        }
        if (trimmed.length() > max) {
            throw ApiException.invalid(field, "TOO_LONG");
        }
        return trimmed;
    }

    /** Optional text: null or only spaces is stored as null; 422 TOO_LONG above the maximum. */
    public static String optional(String value, String field, int max) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.length() > max) {
            throw ApiException.invalid(field, "TOO_LONG");
        }
        return trimmed;
    }
}
