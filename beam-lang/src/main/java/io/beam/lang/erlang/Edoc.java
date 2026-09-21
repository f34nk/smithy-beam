package io.beam.lang.erlang;

public record Edoc(String text) implements FunctionDoc {

  public static Edoc of(String text) {
    return new Edoc(text);
  }
}
