package io.beam.dsl.erlang;

public record NotEqualGuard(Expression left, Expression right) implements Guard {

  public static NotEqualGuard of(Expression left, Expression right) {
    return new NotEqualGuard(left, right);
  }
}
