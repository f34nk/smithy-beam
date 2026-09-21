package io.beam.lang.erlang;

public record Variable(String name) implements Expression {

  public static Variable of(String name) {
    return new Variable(name);
  }
}
