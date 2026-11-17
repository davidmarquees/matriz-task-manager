package pt.matriz.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

/**
 * Unit tests of the task title rule (FR5, A6, E5, D20): mandatory, 3 to 150 characters after trimming,
 * not only spaces. Expected results come from docs/requirements.md and docs/test-plan.md (T1 to T5, T25),
 * not from the current code. Plain JUnit: no Spring context, no database.
 */
class TextInputTest {

    private static final int MIN = 3;   // FR5
    private static final int MAX = 150; // FR5

    private static String title(String value) {
        return TextInput.required(value, "title", MIN, MAX);
    }

    private static void assertRefused(String value, String code) {
        assertThatThrownBy(() -> title(value))
                .isInstanceOfSatisfying(ApiException.class, e -> {
                    assertThat(e.status()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
                    assertThat(e.errors()).containsExactly(new ApiException.FieldError("title", code));
                });
    }

    @Test
    void acceptsAValidTitle() { // T4
        assertThat(title("Fix login")).isEqualTo("Fix login");
    }

    @Test
    void acceptsTheShortestAndLongestTitles() { // T4: boundaries 3 and 150
        assertThat(title("abc")).isEqualTo("abc");
        String longest = "x".repeat(MAX);
        assertThat(title(longest)).isEqualTo(longest);
    }

    @Test
    void trimsSpacesBeforeCheckingTheLength() { // T4, A6
        assertThat(title("  abc  ")).isEqualTo("abc");
        assertThat(title(" " + "x".repeat(MAX) + " ")).hasSize(MAX);
    }

    @Test
    void refusesATitleOneCharacterTooShort() { // T2
        assertRefused("ab", "TOO_SHORT");
        assertRefused("  ab  ", "TOO_SHORT"); // spaces do not count
    }

    @Test
    void refusesATitleOneCharacterTooLong() { // T3
        assertRefused("x".repeat(MAX + 1), "TOO_LONG");
    }

    @Test
    void refusesATitleMadeOnlyOfSpaces() { // T1, E5
        assertRefused("     ", "BLANK");
        assertRefused("", "BLANK");
        assertRefused("\t \n", "BLANK");
    }

    @Test
    void refusesAMissingTitle() { // T5
        assertRefused(null, "REQUIRED");
    }

    @Test
    void refusesATitleMadeOnlyOfNonBreakingSpaces() { // T25: "not only spaces" (A6) includes Unicode spaces
        assertRefused("\u00A0\u00A0\u00A0\u00A0", "BLANK");
    }

    @Test
    void countsAnEmojiAsOneCharacter() { // T25: FR5 limits characters, not UTF-16 units
        String withEmoji = "x".repeat(MAX - 1) + "\uD83D\uDE00"; // 150 characters
        assertThat(withEmoji.codePointCount(0, withEmoji.length())).isEqualTo(MAX);
        assertThat(title(withEmoji)).isEqualTo(withEmoji);
    }
}
