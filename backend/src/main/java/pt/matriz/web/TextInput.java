package pt.matriz.web;

/**
 * Validation of text fields (A6, A20, docs/database.md): values are trimmed before the length rules
 * are applied; text made only of spaces counts as blank. "Spaces" include Unicode space characters such as
 * the non-breaking space (U+00A0), and lengths count characters (code points), as the contract does
 * (docs/quality-security-review.md, Q-1 and Q-2).
 * <p>
 * Control characters (U+0000 to U+001F and U+007F) are refused with 422 INVALID_CHARACTER (D49): single-line
 * text (titles, names, emails) accepts none; multi-line text (descriptions, comments) accepts only tab, line
 * feed and carriage return. Spaces at the edges are trimmed first, as before.
 */
public final class TextInput {

    private TextInput() {
    }

    /**
     * Mandatory single-line text (titles, names, emails): 422 REQUIRED when missing, BLANK when only spaces,
     * INVALID_CHARACTER with a control character, TOO_SHORT / TOO_LONG on length.
     */
    public static String required(String value, String field, int min, int max) {
        return required(value, field, min, max, false);
    }

    /** Mandatory multi-line text (comments): as {@link #required}, but tab and line breaks are allowed. */
    public static String requiredMultiLine(String value, String field, int min, int max) {
        return required(value, field, min, max, true);
    }

    private static String required(String value, String field, int min, int max, boolean multiLine) {
        if (value == null) {
            throw ApiException.invalid(field, "REQUIRED");
        }
        String trimmed = trim(value);
        if (trimmed.isEmpty()) {
            throw ApiException.invalid(field, "BLANK");
        }
        checkCharacters(trimmed, field, multiLine);
        int length = length(trimmed);
        if (length < min) {
            throw ApiException.invalid(field, "TOO_SHORT");
        }
        if (length > max) {
            throw ApiException.invalid(field, "TOO_LONG");
        }
        return trimmed;
    }

    /**
     * Optional multi-line text (descriptions): null or only spaces is stored as null; 422 INVALID_CHARACTER
     * with a control character other than tab and line breaks; TOO_LONG above the maximum.
     */
    public static String optional(String value, String field, int max) {
        if (value == null) {
            return null;
        }
        String trimmed = trim(value);
        if (trimmed.isEmpty()) {
            return null;
        }
        checkCharacters(trimmed, field, true);
        if (length(trimmed) > max) {
            throw ApiException.invalid(field, "TOO_LONG");
        }
        return trimmed;
    }

    /** D49: control characters are refused; multi-line text keeps tab, line feed and carriage return. */
    private static void checkCharacters(String value, String field, boolean multiLine) {
        boolean refused = value.codePoints().anyMatch(c -> (c <= 0x1F || c == 0x7F)
                && !(multiLine && (c == '\t' || c == '\n' || c == '\r')));
        if (refused) {
            throw ApiException.invalid(field, "INVALID_CHARACTER");
        }
    }

    private static boolean isSpace(int codePoint) {
        return Character.isWhitespace(codePoint) || Character.isSpaceChar(codePoint);
    }

    /** Removes leading and trailing spaces, including Unicode ones that {@link String#trim()} keeps. */
    private static String trim(String value) {
        int start = 0;
        int end = value.length();
        while (start < end) {
            int codePoint = value.codePointAt(start);
            if (!isSpace(codePoint)) {
                break;
            }
            start += Character.charCount(codePoint);
        }
        while (end > start) {
            int codePoint = value.codePointBefore(end);
            if (!isSpace(codePoint)) {
                break;
            }
            end -= Character.charCount(codePoint);
        }
        return value.substring(start, end);
    }

    /** Number of characters (code points), so an emoji counts as one. */
    private static int length(String value) {
        return value.codePointCount(0, value.length());
    }
}
