package io.beam.lang.gleam;

public record Variable(String name) implements Expression {

  public static Variable of(String name) {
    return new Variable(name);
  }
}
