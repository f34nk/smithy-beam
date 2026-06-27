package io.smithy.beam.ir.erlang;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public final class ErlModule implements IrObject {
    private final String moduleName;
    private final List<ErlPreambleEntry> preamble;
    private final List<ErlModuleAttribute> attributes;
    private final List<ErlFunction> functions;
    private final List<ErlPreambleEntry> epilogue;

    public ErlModule(
            String moduleName,
            List<ErlPreambleEntry> preamble,
            List<ErlModuleAttribute> attributes,
            List<ErlFunction> functions) {
        this(moduleName, preamble, attributes, functions, List.of());
    }

    public ErlModule(
            String moduleName,
            List<ErlPreambleEntry> preamble,
            List<ErlModuleAttribute> attributes,
            List<ErlFunction> functions,
            List<ErlPreambleEntry> epilogue) {
        this.moduleName = moduleName;
        this.preamble = List.copyOf(preamble);
        this.attributes = List.copyOf(attributes);
        this.functions = List.copyOf(functions);
        this.epilogue = List.copyOf(epilogue);
    }

    public String moduleName() {
        return moduleName;
    }

    public List<ErlPreambleEntry> preamble() {
        return preamble;
    }

    public List<ErlModuleAttribute> attributes() {
        return attributes;
    }

    public List<ErlFunction> functions() {
        return functions;
    }

    @Override
    public List<String> lines(int indent) {
        List<String> out = new ArrayList<>();
        for (ErlPreambleEntry item : preamble) {
            out.addAll(item.lines(indent));
        }
        out.addAll(new ErlAttribute("module", moduleName).lines(indent));
        for (ErlModuleAttribute attribute : attributes) {
            out.addAll(attribute.lines(indent));
        }
        if (!functions.isEmpty()) {
            out.add("");
        }
        for (int i = 0; i < functions.size(); i++) {
            if (i > 0) {
                out.add("");
            }
            out.addAll(functions.get(i).lines(indent));
        }
        if (!epilogue.isEmpty()) {
            out.add("");
            for (ErlPreambleEntry item : epilogue) {
                out.addAll(item.lines(indent));
            }
        }
        return flattenEmbeddedNewlines(out);
    }

    private static List<String> flattenEmbeddedNewlines(List<String> lines) {
        List<String> out = new ArrayList<>();
        for (String line : lines) {
            if (line.indexOf('\n') < 0) {
                out.add(line);
            } else {
                out.addAll(Arrays.asList(line.split("\n", -1)));
            }
        }
        return out;
    }

    @Override
    public List<String> lines() {
        return lines(0);
    }
}
