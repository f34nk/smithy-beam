package io.beam.lang.erlang;

public record Callback(String name, String inputTypes, String outputTypes, FunctionDoc docOrNull) {

  public static Callback of(String name, String inputTypes, String outputTypes) {
    return new Callback(name, inputTypes, outputTypes, null);
  }

  public static Callback of(
      String name, String inputTypes, String outputTypes, FunctionDoc docOrNull) {
    return new Callback(name, inputTypes, outputTypes, docOrNull);
  }
}
