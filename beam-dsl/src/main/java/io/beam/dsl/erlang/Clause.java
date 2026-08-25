package io.beam.dsl.erlang;

public record Clause(Pattern pattern, Guard guard, Expression body) {

  public static Clause of(Pattern pattern, Expression body) {
    return new Clause(pattern, null, body);
  }

  public static Clause of(Pattern pattern, Guard guard, Expression body) {
    return new Clause(pattern, guard, body);
  }
}
