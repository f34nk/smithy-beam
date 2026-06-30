package io.smithy.beam.elixir;

import io.smithy.beam.ir.elixir.ExFunction;
import java.util.ArrayList;
import java.util.List;

final class ElixirClientModuleBuilder {
  private final List<ExFunction> serviceFunctions = new ArrayList<>();
  private final List<ExFunction> operationFunctions = new ArrayList<>();

  void addServiceFunctions(List<ExFunction> functions) {
    serviceFunctions.addAll(functions);
  }

  void addOperationFunctions(List<ExFunction> functions) {
    operationFunctions.addAll(functions);
  }

  void addOperationFunction(ExFunction function) {
    operationFunctions.add(function);
  }

  List<ExFunction> serviceFunctions() {
    return serviceFunctions;
  }

  List<ExFunction> operationFunctions() {
    return operationFunctions;
  }
}
