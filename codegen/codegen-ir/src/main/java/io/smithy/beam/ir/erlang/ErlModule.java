package io.smithy.beam.ir.erlang;

import java.util.ArrayList;
import java.util.List;

public final class ErlModule implements IrObject {
    private final String moduleName;
    private final List<ErlPreambleEntry> preamble;
    private final List<ErlModuleAttribute> attributes;
    private final List<ErlFunction> functions;

    public ErlModule(
            String moduleName,
            List<ErlPreambleEntry> preamble,
            List<ErlModuleAttribute> attributes,
            List<ErlFunction> functions) {
        this.moduleName = moduleName;
        this.preamble = List.copyOf(preamble);
        this.attributes = List.copyOf(attributes);
        this.functions = List.copyOf(functions);
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
        return out;
    }

    @Override
    public List<String> lines() {
        return lines(0);
    }
}
