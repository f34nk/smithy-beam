package io.smithy.beam.ir.erlang;

import java.util.ArrayList;
import java.util.List;

public final class ErlTypeDef implements ErlHeaderEntry, ErlModuleAttribute {
    private final String name;
    private final String body;
    private final List<ErlComment> preamble;
    private final List<String> variants;

    public ErlTypeDef(String name, String body) {
        this(name, body, List.of(), List.of());
    }

    public ErlTypeDef(String name, String body, List<ErlComment> preamble) {
        this(name, body, preamble, List.of());
    }

    private ErlTypeDef(String name, String body, List<ErlComment> preamble, List<String> variants) {
        this.name = name;
        this.body = body;
        this.preamble = List.copyOf(preamble);
        this.variants = List.copyOf(variants);
    }

    public static ErlTypeDef unionType(String name, List<String> variants) {
        return new ErlTypeDef(name, String.join(" | ", variants), List.of(), variants);
    }

    public String name() {
        return name;
    }

    public String body() {
        return body;
    }

    @Override
    public List<String> lines(int indent) {
        List<String> out = new ArrayList<>();
        for (ErlComment comment : preamble) {
            out.addAll(comment.lines(indent));
        }
        out.addAll(typeBodyLines(indent));
        return out;
    }

    private List<String> typeBodyLines(int indent) {
        String typeName = typeDeclName();
        if (body.indexOf('\n') >= 0) {
            return multilineTypeBodyLines(indent, typeName);
        }
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

    private List<String> multilineTypeBodyLines(int indent, String typeName) {
        List<String> out = new ArrayList<>();
        String[] bodyLines = body.split("\n", -1);
        out.add(IrObject.indent(indent) + "-type " + typeName + " :: " + bodyLines[0]);
        for (int i = 1; i < bodyLines.length - 1; i++) {
            out.add(IrObject.indent(indent) + bodyLines[i]);
        }
        out.add(IrObject.indent(indent) + bodyLines[bodyLines.length - 1] + ".");
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
