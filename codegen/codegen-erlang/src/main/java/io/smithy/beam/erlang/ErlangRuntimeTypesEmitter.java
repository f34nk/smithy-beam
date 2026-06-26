package io.smithy.beam.erlang;

import java.util.Optional;

public final class ErlangRuntimeTypesEmitter {

    private ErlangRuntimeTypesEmitter() {}

    public static void writeBody(ErlangWriter writer, Optional<String> endpointRuleSetMap) {
        writer.write(
                "$L",
                ErlangRuntimeTypesIr.runtimeTypesHeader("runtime_types", endpointRuleSetMap).asString());
    }
}
