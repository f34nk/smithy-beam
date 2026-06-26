package io.smithy.beam.ir.erlang;

import java.util.ArrayList;
import java.util.List;

public final class ErlTypeDef implements ErlHeaderEntry {
    private final String name;
    private final String body;
    private final List<String> variants;

    public ErlTypeDef(String name, String body) {
        this(name, body, List.of());
    }

    private ErlTypeDef(String name, String body, List<String> variants) {
        this.name = name;
        this.body = body;
        this.variants = List.copyOf(variants);
    }

    public static ErlTypeDef unionType(String name, List<String> variants) {
        return new ErlTypeDef(name, String.join(" | ", variants), variants);
    }

    public String name() {
        return name;
    }

    public String body() {
        return body;
    }

    @Override
    public List<String> lines(int indent) {
        String typeName = typeDeclName();
        if (variants.size() <= 2) {
            return List.of(IrObject.indent(indent) + "-type " + typeName + " :: " + body + ".");
        }
        List<String> out = new ArrayList<>();
        out.add(IrObject.indent(indent) + "-type " + typeName + " ::");
        for (int i = 0; i < variants.size(); i++) {
            if (i == 0) {
                out.add(IrObject.indent(indent + 1) + variants.get(i));
            } else {
                String suffix = (i < variants.size() - 1) ? "" : ".";
                out.add(IrObject.indent(indent + 1) + "| " + variants.get(i) + suffix);
            }
        }
        return out;
    }

    private String typeDeclName() {
        return name.endsWith("()") ? name : name + "()";
    }

    @Override
    public List<String> lines() {
        return lines(0);
    }
}
