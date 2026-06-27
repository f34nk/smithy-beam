package io.smithy.beam.ir.erlang;

import java.util.ArrayList;
import java.util.List;

public final class ErlCallbackSpec implements ErlModuleAttribute {
    private static final int SPEC_LINE_LIMIT = 100;

    private final String name;
    private final String inputTypes;
    private final String outputTypes;
    private final ErlFunctionDoc docOrNull;

    public ErlCallbackSpec(String name, String inputTypes, String outputTypes) {
        this(name, inputTypes, outputTypes, null);
    }

    public ErlCallbackSpec(String name, String inputTypes, String outputTypes, ErlFunctionDoc docOrNull) {
        this.name = name;
        this.inputTypes = inputTypes;
        this.outputTypes = outputTypes;
        this.docOrNull = docOrNull;
    }

    public static ErlCallbackSpec callbackSpec(String name, String inputTypes, String outputTypes) {
        return new ErlCallbackSpec(name, inputTypes, outputTypes);
    }

    public static ErlCallbackSpec callbackSpec(
            String name, String inputTypes, String outputTypes, ErlFunctionDoc docOrNull) {
        return new ErlCallbackSpec(name, inputTypes, outputTypes, docOrNull);
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
        List<String> out = new ArrayList<>();
        if (docOrNull != null) {
            out.addAll(docOrNull.lines(indent));
        }
        out.addAll(callbackLines(indent));
        return out;
    }

    private List<String> callbackLines(int indent) {
        String body = name + "(" + inputTypes + ") -> " + outputTypes;
        String line = "-callback " + body;
        if (line.length() <= SPEC_LINE_LIMIT || body.indexOf(" -> ") < 0) {
            return List.of(IrObject.indent(indent) + line + ".");
        }
        int split = body.indexOf(" -> ");
        return List.of(
                IrObject.indent(indent) + "-callback " + body.substring(0, split) + " ->",
                IrObject.indent(indent + 1) + body.substring(split + 4) + ".");
    }

    @Override
    public List<String> lines() {
        return lines(0);
    }
}
