package io.beam.dsl.erlang;

/**
 * Module documentation. {@code text} is raw content; the renderer escapes for the target language.
 */
public record Moduledoc(String text) {

  public static Moduledoc of(String text) {
    return new Moduledoc(text);
  }
}
