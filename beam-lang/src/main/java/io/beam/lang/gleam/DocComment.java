package io.beam.lang.gleam;

/**
 * Documentation comment text. Module docs use {@code ////}; type docs use {@code ///}. The renderer
 * splits on newlines and prefixes each line.
 */
public record DocComment(String text) {

  public static DocComment of(String text) {
    return new DocComment(text);
  }
}
