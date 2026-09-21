package io.beam.lang.erlang;

public record Doc(String text) implements FunctionDoc {

  public static Doc of(String text) {
    return new Doc(text);
  }
}
