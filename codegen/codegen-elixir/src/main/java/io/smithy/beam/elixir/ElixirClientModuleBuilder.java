package io.smithy.beam.elixir;

import io.smithy.beam.ir.elixir.ExFunction;
import java.util.ArrayList;
import java.util.List;

final class ElixirClientModuleBuilder {
  private final List<ExFunction> operationFunctions = new ArrayList<>();

  void addOperationFunctions(List<ExFunction> functions) {
    operationFunctions.addAll(functions);
  }

  void addOperationFunction(ExFunction function) {
    operationFunctions.add(function);
  }

  List<ExFunction> operationFunctions() {
    return operationFunctions;
  }
}
