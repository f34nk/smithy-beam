package io.smithy.beam.elixir;

import io.smithy.beam.ir.elixir.ExFunction;
import java.util.ArrayList;
import java.util.List;

final class ElixirServerModuleBuilder {
  private final List<ExFunction> operationFunctions = new ArrayList<>();
  private final List<ExFunction> discoveryFunctions = new ArrayList<>();

  void addOperationFunction(ExFunction function) {
    operationFunctions.add(function);
  }

  void addDiscoveryFunctions(List<ExFunction> functions) {
    discoveryFunctions.addAll(functions);
  }

  List<ExFunction> operationFunctions() {
    return operationFunctions;
  }

  List<ExFunction> discoveryFunctions() {
    return discoveryFunctions;
  }
}
