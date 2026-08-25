package io.beam.dsl.erlang;

import java.util.List;

public record Function(String name, List<FunctionClause> clauses, Spec spec, FunctionDoc doc) {

  public static Function of(String name, List<FunctionClause> clauses) {
    return new Function(name, clauses, null, null);
  }

  public static Function of(String name, List<FunctionClause> clauses, Spec spec) {
    return new Function(name, clauses, spec, null);
  }

  public static Function of(String name, List<FunctionClause> clauses, Spec spec, FunctionDoc doc) {
    return new Function(name, clauses, spec, doc);
  }

  public int arity() {
    if (clauses.isEmpty()) {
      return 0;
    }
    return clauses.get(0).patterns().size();
  }
}
