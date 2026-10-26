package io.beam.lang.gleam;

/**
 * A field inside a constructor pattern.
 *
 * <ul>
 *   <li>positional {@code Ok(x)}: label null, pattern set
 *   <li>labelled {@code Person(name: n)}: label and pattern set
 *   <li>shorthand {@code Person(name:)}: label set, pattern null
 * </ul>
 */
public record PatternField(String labelOrNull, Pattern patternOrNull) {

  public static PatternField positional(Pattern pattern) {
    return new PatternField(null, pattern);
  }

  public static PatternField labelled(String label, Pattern pattern) {
    return new PatternField(label, pattern);
  }

  public static PatternField shorthand(String label) {
    return new PatternField(label, null);
  }
}
