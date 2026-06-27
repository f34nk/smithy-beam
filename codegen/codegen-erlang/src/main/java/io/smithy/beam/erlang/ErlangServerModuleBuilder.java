package io.smithy.beam.erlang;

import io.smithy.beam.ir.erlang.ErlFunction;
import java.util.ArrayList;
import java.util.List;

final class ErlangServerModuleBuilder {
  private final List<ErlFunction> operationFunctions = new ArrayList<>();
  private final List<ErlFunction> discoveryFunctions = new ArrayList<>();

  void addOperationFunction(ErlFunction function) {
    operationFunctions.add(function);
  }

  void addDiscoveryFunctions(List<ErlFunction> functions) {
    discoveryFunctions.addAll(functions);
  }

  List<ErlFunction> operationFunctions() {
    return operationFunctions;
  }

  List<ErlFunction> discoveryFunctions() {
    return discoveryFunctions;
  }
}
