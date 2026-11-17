// Text lengths counted in characters (Unicode code points), as the API counts them (FR5, Q-1/Q-2, Q-3).
// String.length and the HTML maxlength attribute count UTF-16 units, so an emoji would count as two.

/** Number of characters (code points) in the text. */
export function charCount(value: string): number {
  return Array.from(value).length;
}

/** The text cut to at most `max` characters (code points), never splitting an emoji. */
export function limitChars(value: string, max: number): string {
  const chars = Array.from(value);
  return chars.length > max ? chars.slice(0, max).join('') : value;
}
