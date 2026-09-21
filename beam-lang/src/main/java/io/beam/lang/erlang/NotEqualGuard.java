package io.beam.lang.erlang;

public record NotEqualGuard(Expression left, Expression right) implements Guard {

  public static NotEqualGuard of(Expression left, Expression right) {
    return new NotEqualGuard(left, right);
  }
}
