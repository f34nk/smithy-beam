package io.beam.dsl.elixir;

import java.util.List;

public record WithExpr(
    List<WithBinding> bindings, Expression body, List<WithElseClause> elseClauses)
    implements Expression {

  public static WithExpr of(List<WithBinding> bindings, Expression body) {
    return new WithExpr(List.copyOf(bindings), body, List.of());
  }

  public static WithExpr of(
      List<WithBinding> bindings, Expression body, List<WithElseClause> elseClauses) {
    return new WithExpr(List.copyOf(bindings), body, List.copyOf(elseClauses));
  }
}
