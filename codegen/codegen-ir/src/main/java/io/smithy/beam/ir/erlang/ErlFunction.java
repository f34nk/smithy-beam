package io.smithy.beam.ir.erlang;

import java.util.ArrayList;
import java.util.List;

public final class ErlFunction implements IrObject {
  private final String name;
  private final int arity;
  private final ErlFunctionDoc docOrNull;
  private final ErlFunctionSpec specOrNull;
  private final List<ErlClause> clauses;

  public ErlFunction(
      String name,
      int arity,
      ErlFunctionDoc docOrNull,
      ErlFunctionSpec specOrNull,
      List<ErlClause> clauses) {
    this.name = name;
    this.arity = arity;
    this.docOrNull = docOrNull;
    this.specOrNull = specOrNull;
    this.clauses = List.copyOf(clauses);
  }

  public String name() {
    return name;
  }

  public int arity() {
    return arity;
  }

  public ErlFunctionDoc docOrNull() {
    return docOrNull;
  }

  public ErlFunctionSpec specOrNull() {
    return specOrNull;
  }

  public List<ErlClause> clauses() {
    return clauses;
  }

  public static ErlFunction function(String name, int arity, List<ErlClause> clauses) {
    return new ErlFunction(name, arity, null, null, clauses);
  }

  public static ErlFunction functionWithSpec(
      String name, int arity, ErlFunctionSpec spec, List<ErlClause> clauses) {
    return new ErlFunction(name, arity, null, spec, clauses);
  }

  public static ErlFunction functionWithDocAndSpec(
      String name, int arity, ErlFunctionDoc doc, ErlFunctionSpec spec, List<ErlClause> clauses) {
    return new ErlFunction(name, arity, doc, spec, clauses);
  }

  public static ErlFunction functionWithSpec(
      String name, int arity, String inputTypes, String outputTypes, List<ErlClause> clauses) {
    return functionWithSpec(
        name, arity, ErlFunctionSpec.functionSpec(name, inputTypes, outputTypes), clauses);
  }

  @Override
  public String asString() {
    return IrObject.super.asString();
  }

  @Override
  public List<String> lines(int indent) {
    List<String> out = new ArrayList<>();
    if (docOrNull != null) {
      out.addAll(docOrNull.lines(indent));
    }
    if (specOrNull != null) {
      out.addAll(specOrNull.lines(indent));
    }
    for (int i = 0; i < clauses.size(); i++) {
      out.addAll(clauses.get(i).lines(indent, name, i < clauses.size() - 1));
    }
    return out;
  }

  @Override
  public List<String> lines() {
    return lines(0);
  }
}
