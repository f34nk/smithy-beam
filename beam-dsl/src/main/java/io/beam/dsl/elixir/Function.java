package io.beam.dsl.elixir;

import java.util.List;

public record Function(
    String name,
    boolean private_,
    List<FunctionHead> heads,
    Expression body,
    Spec specOrNull,
    FunctionDoc docOrNull,
    boolean oneLiner) {

  public static Function of(
      String name,
      boolean private_,
      List<FunctionHead> heads,
      Expression body,
      Spec spec,
      FunctionDoc doc,
      boolean oneLiner) {
    return new Function(name, private_, heads, body, spec, doc, oneLiner);
  }

  public static Function of(
      String name, boolean private_, List<FunctionHead> heads, Expression body, boolean oneLiner) {
    return new Function(name, private_, heads, body, null, null, oneLiner);
  }
}
