package io.beam.dsl.elixir;

import java.util.List;

public record Callback(String name, List<String> params, String returnType, FunctionDoc docOrNull) {

  public static Callback of(String name, List<String> params, String returnType) {
    return new Callback(name, params, returnType, null);
  }

  public static Callback of(
      String name, List<String> params, String returnType, FunctionDoc docOrNull) {
    return new Callback(name, List.copyOf(params), returnType, docOrNull);
  }
}
