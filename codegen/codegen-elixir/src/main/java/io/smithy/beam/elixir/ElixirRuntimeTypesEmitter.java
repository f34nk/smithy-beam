package io.smithy.beam.elixir;

public final class ElixirRuntimeTypesEmitter {

  private ElixirRuntimeTypesEmitter() {}

  public static void writeBody(ElixirWriter writer, String moduleName) {
    writer.write("$L", ElixirRuntimeTypesIr.runtimeTypesModule(moduleName).asString());
  }
}
