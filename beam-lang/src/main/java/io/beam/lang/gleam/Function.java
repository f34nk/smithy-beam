package io.beam.lang.gleam;

import java.util.List;

/**
 * A Gleam function. {@code body} is the function body source (statements / expression without
 * surrounding braces).
 */
public record Function(
    String name,
    List<Param> params,
    String returnTypeOrNull,
    String body,
    DocComment docOrNull,
    boolean pub) {

  public static Function pub(String name, List<Param> params, String returnType, String body) {
    return new Function(name, List.copyOf(params), returnType, body, null, true);
  }

  public static Function pub(
      String name, List<Param> params, String returnType, String body, DocComment doc) {
    return new Function(name, List.copyOf(params), returnType, body, doc, true);
  }

  public static Function pubTodo(String name, List<Param> params, String returnType) {
    return pub(name, params, returnType, "todo");
  }
}
