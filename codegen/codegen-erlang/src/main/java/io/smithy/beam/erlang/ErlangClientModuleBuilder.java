package io.smithy.beam.erlang;

import io.beam.dsl.erlang.Function;
import java.util.ArrayList;
import java.util.List;

final class ErlangClientModuleBuilder {
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
