package io.smithy.beam.ir.erlang;

import java.util.List;

public final class ErlFunctionSpec implements IrObject {
    private static final int SPEC_LINE_LIMIT = 100;

    private final String name;
    private final String inputTypes;
    private final String outputTypes;

    public ErlFunctionSpec(String name, String inputTypes, String outputTypes) {
        this.name = name;
        this.inputTypes = inputTypes;
        this.outputTypes = outputTypes;
    }

    public static ErlFunctionSpec functionSpec(String name, String inputTypes, String outputTypes) {
        return new ErlFunctionSpec(name, inputTypes, outputTypes);
    }

    public String name() {
        return name;
    }

    public String inputTypes() {
        return inputTypes;
    }

    public String outputTypes() {
        return outputTypes;
    }

    @Override
    public List<String> lines(int indent) {
        String body = name + "(" + inputTypes + ") -> " + outputTypes;
        String line = "-spec " + body;
        if (line.length() <= SPEC_LINE_LIMIT || body.indexOf(" -> ") < 0) {
            return List.of(IrObject.indent(indent) + line + ".");
        }
        int split = body.indexOf(" -> ");
        return List.of(
                IrObject.indent(indent) + "-spec " + body.substring(0, split) + " ->",
                IrObject.indent(indent + 1) + body.substring(split + 4) + ".");
    }

    @Override
    public List<String> lines() {
        return lines(0);
    }
}
