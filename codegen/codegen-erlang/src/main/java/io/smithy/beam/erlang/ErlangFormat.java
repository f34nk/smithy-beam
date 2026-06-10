package io.smithy.beam.erlang;

import java.util.List;

/**
 * Shared formatting helpers for generated Erlang output.
 *
 * <p>Encodes erlfmt-style layout rules used across emitters so generated code
 * matches idiomatic Erlang without a post-processing pass.
 */
final class ErlangFormat {

    static final int SPEC_LINE_LIMIT = 100;
    static final int SINGLE_LINE_CLAUSE_LIMIT = 72;

    private ErlangFormat() {}

    static void writeSpec(ErlangWriter writer, String signature) {
        String line = "-spec " + signature;
        int split = signature.indexOf(" -> ");
        if (split < 0 || line.length() <= SPEC_LINE_LIMIT) {
            writer.write("-spec $L", signature);
            return;
        }
        String head = signature.substring(0, split);
        String tail = signature.substring(split + 4);
        writer.write("-spec $L ->", head);
        writer.write("    $L", tail);
    }

    static void writeExport(ErlangWriter writer, List<String> exports) {
        if (exports.isEmpty()) {
            writer.write("-export([]).");
            return;
        }
        if (String.join(", ", exports).length() <= SPEC_LINE_LIMIT) {
            writer.write("-export([$L]).", String.join(", ", exports));
            return;
        }
        writer.write("-export([");
        writer.indent();
        for (int i = 0; i < exports.size(); i++) {
            String suffix = (i < exports.size() - 1) ? "," : "";
            writer.write("$L$L", exports.get(i), suffix);
        }
        writer.dedent();
        writer.write("]).");
    }

    static void writeUnionType(ErlangWriter writer, String name, List<String> variants) {
        if (variants.size() <= 2) {
            writer.write("-type $L :: $L.", name, String.join(" | ", variants));
            return;
        }
        writer.write("-type $L ::", name);
        for (int i = 0; i < variants.size(); i++) {
            if (i == 0) {
                writer.write("    $L", variants.get(i));
            } else {
                String suffix = (i < variants.size() - 1) ? "" : ".";
                writer.write("    | $L$L", variants.get(i), suffix);
            }
        }
    }

    static void writeFiltermap(ErlangWriter writer, List<String> funClauses, String args) {
        writer.write("lists:filtermap(");
        writer.indent();
        writer.write("fun");
        writer.indent();
        for (String clause : funClauses) {
            writer.write("$L", clause);
        }
        writer.dedent();
        writer.write("end,");
        writer.write("[$L]", args);
        writer.dedent();
        writer.write(")");
    }

    static void beginBinding(ErlangWriter writer, String varName) {
        writer.write("$L =", varName);
        writer.indent();
    }

    static void endBinding(ErlangWriter writer) {
        writer.dedent();
    }

    static void breakFunctionHead(ErlangWriter writer, String name, List<String> args) {
        if (args.isEmpty()) {
            writer.write("$L() ->", name);
            return;
        }
        writer.write("$L(", name);
        writer.indent();
        for (int i = 0; i < args.size(); i++) {
            String suffix = (i < args.size() - 1) ? "," : "";
            writer.write("$L$L", args.get(i), suffix);
        }
        writer.dedent();
        writer.write(") ->");
    }

    static void writeRecordFunctionHead(
            ErlangWriter writer,
            String functionName,
            String binding,
            String recordName,
            List<String> fields) {
        writer.write("$L(", functionName);
        writer.indent();
        writer.write("$L = #$L{", binding, recordName);
        writer.indent();
        for (int i = 0; i < fields.size(); i++) {
            String suffix = (i < fields.size() - 1) ? "," : "";
            writer.write("$L$L", fields.get(i), suffix);
        }
        writer.dedent();
        writer.write("}");
        writer.dedent();
        writer.write(") ->");
    }

    static String formatRecordField(String fieldName, String expression) {
        String full = "    " + fieldName + " = " + expression;
        if (full.length() <= SPEC_LINE_LIMIT) {
            return full;
        }
        int open = expression.indexOf('(');
        if (open < 0) {
            return full;
        }
        return "    " + fieldName + " = " + expression.substring(0, open + 1)
                + "\n        " + expression.substring(open + 1);
    }

    static void writeListComprehension(
            ErlangWriter writer,
            String varName,
            String expr,
            List<String> generators,
            List<String> filters) {
        writer.write("$L = [", varName);
        writer.indent();
        writer.write("$L", expr);
        for (String generator : generators) {
            writer.write(" || $L", generator);
        }
        for (String filter : filters) {
            writer.write("    $L", filter);
        }
        writer.dedent();
        writer.write("],");
    }
}
