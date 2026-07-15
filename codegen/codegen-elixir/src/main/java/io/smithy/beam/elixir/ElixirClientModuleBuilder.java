package io.smithy.beam.elixir;

import io.beam.ir.elixir.Function;
import java.util.ArrayList;
import java.util.List;

final class ElixirClientModuleBuilder {
  private final List<Function> operationFunctions = new ArrayList<>();

  void addOperationFunctions(List<Function> functions) {
    operationFunctions.addAll(functions);
  }

  void addOperationFunction(Function function) {
    operationFunctions.add(function);
  }

  List<Function> operationFunctions() {
    return operationFunctions;
  }
}
