package io.smithy.beam.erlang;

import io.smithy.beam.ir.erlang.ErlFunction;
import io.smithy.beam.ir.erlang.ErlFunctionSpec;

final class ErlangInfrastructureIr {
    private ErlangInfrastructureIr() {}

    static void writeSpec(ErlangWriter writer, String name, String inputTypes, String outputTypes) {
        writer.write("$L", ErlFunctionSpec.functionSpec(name, inputTypes, outputTypes).asString());
    }

    static void writeFunction(ErlangWriter writer, ErlFunction fn) {
        for (String line : fn.lines()) {
            writer.write(line);
        }
        writer.write("");
    }
}
