package io.smithy.beam.erlang;

import io.beam.ir.erlang.Function;
import java.util.ArrayList;
import java.util.List;

final class ErlangClientModuleBuilder {
  private final List<Function> serviceFunctions = new ArrayList<>();
  private final List<Function> operationFunctions = new ArrayList<>();

  void addServiceFunctions(List<Function> functions) {
    serviceFunctions.addAll(functions);
  }

  void addOperationFunctions(List<Function> functions) {
    operationFunctions.addAll(functions);
  }

  void addOperationFunction(Function function) {
    operationFunctions.add(function);
  }

  List<Function> serviceFunctions() {
    return serviceFunctions;
  }

  List<Function> operationFunctions() {
    return operationFunctions;
  }
}
