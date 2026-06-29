package io.smithy.beam.ir.elixir;

import java.util.ArrayList;
import java.util.List;

public final class ExFunction implements IrObject, ExModuleEntry {
  private final String keyword;
  private final String name;
  private final ExDoc docOrNull;
  private final ExSpec specOrNull;
  private final ExImplAttr implOrNull;
  private final List<ExClause> clauses;

  public ExFunction(
      String keyword,
      String name,
      ExDoc docOrNull,
      ExSpec specOrNull,
      ExImplAttr implOrNull,
      List<ExClause> clauses) {
    this.keyword = keyword;
    this.name = name;
    this.docOrNull = docOrNull;
    this.specOrNull = specOrNull;
    this.implOrNull = implOrNull;
    this.clauses = List.copyOf(clauses);
  }

  public ExFunction(
      String keyword,
      String name,
      ExDoc docOrNull,
      ExSpec specOrNull,
      List<ExClause> clauses) {
    this(keyword, name, docOrNull, specOrNull, null, clauses);
  }

  public String keyword() {
    return keyword;
  }

  public String name() {
    return name;
  }

  public ExDoc docOrNull() {
    return docOrNull;
  }

  public ExSpec specOrNull() {
    return specOrNull;
  }

  public List<ExClause> clauses() {
    return clauses;
  }

  public static ExFunction defFunction(String name, List<ExClause> clauses) {
    return new ExFunction("def", name, null, null, clauses);
  }

  public static ExFunction defFunctionWithImpl(String name, List<ExClause> clauses) {
    return new ExFunction("def", name, null, null, ExImplAttr.implTrue(), clauses);
  }

  public static ExFunction defpFunction(String name, List<ExClause> clauses) {
    return new ExFunction("defp", name, null, null, clauses);
  }

  public static ExFunction functionWithDocAndSpec(
      String keyword, String name, ExDoc doc, ExSpec spec, List<ExClause> clauses) {
    return new ExFunction(keyword, name, doc, spec, null, clauses);
  }

  public static ExFunction functionWithSpec(
      String keyword, String name, ExSpec spec, List<ExClause> clauses) {
    return new ExFunction(keyword, name, null, spec, null, clauses);
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
    if (implOrNull != null) {
      out.addAll(implOrNull.lines(indent));
    }
    for (int i = 0; i < clauses.size(); i++) {
      out.addAll(clauses.get(i).lines(indent, keyword, name, i < clauses.size() - 1));
    }
    return out;
  }

  @Override
  public List<String> lines() {
    return lines(0);
  }
}
