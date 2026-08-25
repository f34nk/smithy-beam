package io.beam.dsl.elixir;

/**
 * Function documentation. {@code text} is raw content; the renderer escapes for the target
 * language.
 */
public record FunctionDoc(String text) {

  public static FunctionDoc of(String text) {
    return new FunctionDoc(text);
  }
}
