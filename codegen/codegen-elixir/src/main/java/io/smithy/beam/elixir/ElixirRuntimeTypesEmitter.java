package io.smithy.beam.elixir;

import java.util.Optional;

public final class ElixirRuntimeTypesEmitter {

  private ElixirRuntimeTypesEmitter() {}

  public static void writeBody(
      ElixirWriter writer, String moduleName, Optional<String> endpointRuleSetJson) {
    writer.write(
        "$L", ElixirRuntimeTypesIr.runtimeTypesModule(moduleName, endpointRuleSetJson).asString());
  }
}
