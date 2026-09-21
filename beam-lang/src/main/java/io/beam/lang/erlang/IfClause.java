package io.beam.lang.erlang;

public record IfClause(Guard guard, Expression body) {

  public static IfClause of(Guard guard, Expression body) {
    return new IfClause(guard, body);
  }
}
