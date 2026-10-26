package io.beam.lang.gleam;

import java.util.List;

/**
 * A Gleam function. {@code body} is an expression tree (often a {@link BlockExpr} or {@link
 * RawExpr}).
 */
public record Function(
    String name,
    List<Param> params,
    String returnTypeOrNull,
    Expression body,
    DocComment docOrNull,
    boolean pub) {

  public static Function pub(String name, List<Param> params, String returnType, Expression body) {
    return new Function(name, List.copyOf(params), returnType, body, null, true);
  }

  public static Function pub(
      String name, List<Param> params, String returnType, Expression body, DocComment doc) {
    return new Function(name, List.copyOf(params), returnType, body, doc, true);
  }

  public static Function pub(String name, List<Param> params, String returnType, String body) {
    return pub(name, params, returnType, RawExpr.of(body));
  }

  public static Function pub(
      String name, List<Param> params, String returnType, String body, DocComment doc) {
    return pub(name, params, returnType, RawExpr.of(body), doc);
  }

  public static Function pubTodo(String name, List<Param> params, String returnType) {
    return pub(name, params, returnType, RawExpr.of("todo"));
  }
}
