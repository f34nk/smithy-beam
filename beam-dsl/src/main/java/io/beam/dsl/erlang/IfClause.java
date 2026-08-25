package io.beam.dsl.erlang;

public record IfClause(Guard guard, Expression body) {

  public static IfClause of(Guard guard, Expression body) {
    return new IfClause(guard, body);
  }
}
