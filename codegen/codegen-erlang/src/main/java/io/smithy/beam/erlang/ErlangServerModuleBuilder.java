package io.smithy.beam.erlang;

import io.beam.dsl.erlang.Function;
import java.util.ArrayList;
import java.util.List;

final class ErlangServerModuleBuilder {
  private final List<Function> operationFunctions = new ArrayList<>();
  private final List<Function> discoveryFunctions = new ArrayList<>();

  void addOperationFunction(Function function) {
    operationFunctions.add(function);
  }

  void addDiscoveryFunctions(List<Function> functions) {
    discoveryFunctions.addAll(functions);
  }

  List<Function> operationFunctions() {
    return operationFunctions;
  }

  List<Function> discoveryFunctions() {
    return discoveryFunctions;
  }
}
