package io.beam.dsl.elixir;

public record Clause(Pattern pattern, Guard guardOrNull, Expression body) {

  public static Clause of(Pattern pattern, Expression body) {
    return new Clause(pattern, null, body);
  }

  public static Clause of(Pattern pattern, Guard guard, Expression body) {
    return new Clause(pattern, guard, body);
  }
}
