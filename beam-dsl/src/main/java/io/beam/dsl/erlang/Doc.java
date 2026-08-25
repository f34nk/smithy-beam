package io.beam.dsl.erlang;

public record Doc(String text) implements FunctionDoc {

  public static Doc of(String text) {
    return new Doc(text);
  }
}
