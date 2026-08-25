package io.beam.dsl.erlang;

public record IsTypeGuard(String type, Expression expression) implements Guard {

  public static IsTypeGuard of(String type, Expression expression) {
    return new IsTypeGuard(type, expression);
  }
}
