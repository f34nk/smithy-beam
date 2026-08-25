package io.beam.dsl.elixir;

/**
 * Module documentation. {@code text} is raw content; the renderer escapes for the target language.
 */
public record Moduledoc(String text, boolean literal) {

  public static Moduledoc of(String text) {
    return new Moduledoc(text, false);
  }

  public static Moduledoc falseLiteral() {
    return new Moduledoc("false", true);
  }
}
