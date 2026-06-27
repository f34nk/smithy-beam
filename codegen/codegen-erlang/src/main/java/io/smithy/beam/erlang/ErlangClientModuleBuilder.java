package io.smithy.beam.erlang;

import io.smithy.beam.ir.erlang.ErlFunction;

import java.util.ArrayList;
import java.util.List;

final class ErlangClientModuleBuilder {
    private final List<ErlFunction> serviceFunctions = new ArrayList<>();
    private final List<ErlFunction> operationFunctions = new ArrayList<>();

    void addServiceFunctions(List<ErlFunction> functions) {
        serviceFunctions.addAll(functions);
    }

    void addOperationFunctions(List<ErlFunction> functions) {
        operationFunctions.addAll(functions);
    }

    void addOperationFunction(ErlFunction function) {
        operationFunctions.add(function);
    }

    List<ErlFunction> serviceFunctions() {
        return serviceFunctions;
    }

    List<ErlFunction> operationFunctions() {
        return operationFunctions;
    }
}
