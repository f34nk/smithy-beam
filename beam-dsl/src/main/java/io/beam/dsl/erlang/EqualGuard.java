package io.beam.dsl.erlang;

public record EqualGuard(Expression left, Expression right) implements Guard {

  public static EqualGuard of(Expression left, Expression right) {
    return new EqualGuard(left, right);
  }
}
