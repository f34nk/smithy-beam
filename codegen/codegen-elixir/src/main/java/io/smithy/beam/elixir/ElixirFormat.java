package io.smithy.beam.elixir;

import java.util.List;

/**
 * Shared formatting helpers for generated Elixir output.
 *
 * <p>Encodes mix-format-style layout rules used across emitters so generated code
 * matches idiomatic Elixir without a post-processing pass.
 */
final class ElixirFormat {

    static final int SPEC_LINE_LIMIT = 98;

    private ElixirFormat() {}

    /**
     * Writes an {@code @spec} or {@code @callback} line, splitting at {@code ::} when the
     * signature exceeds {@link #SPEC_LINE_LIMIT} characters.
     */
    static void writeSpec(ElixirWriter writer, String annotation, String signature) {
        String line = annotation + " " + signature;
        int split = signature.indexOf(" :: ");
        if (split < 0 || line.length() <= SPEC_LINE_LIMIT) {
            writer.write("$L $L", annotation, signature);
            return;
        }
        String head = signature.substring(0, split);
        String tail = signature.substring(split + 4);
        writer.write("$L $L ::", annotation, head);
        writer.write("        $L", tail);
    }

    /**
     * Writes an {@code @spec} or {@code @callback} from name, parameters, and return type.
     */
    static void writeSpec(ElixirWriter writer, String annotation, String name, String params, String returnType) {
        writeSpec(writer, annotation, name + "(" + params + ") :: " + returnType);
    }

    /**
     * Begins a pipeline binding: {@code varName =} followed by an indented first value line.
     */
    static void beginPipelineBinding(ElixirWriter writer, String varName) {
        writer.write("$L =", varName);
        writer.indent();
    }

    /**
     * Writes a pipeline continuation step starting with {@code |>}.
     */
    static void writePipelineStep(ElixirWriter writer, String step) {
        writer.write("|> $L", step);
    }

    /**
     * Ends a pipeline binding opened by {@link #beginPipelineBinding}.
     */
    static void endPipelineBinding(ElixirWriter writer) {
        writer.dedent();
    }

    /**
     * Writes an optional list entry using a multi-line {@code if(...)} expression.
     */
    static void writeIfInList(ElixirWriter writer, String condition, String doExpr) {
        writer.write("if($L,", condition);
        writer.indent();
        writer.write("do: $L,", doExpr);
        writer.write("else: nil");
        writer.dedent();
        writer.write(")");
    }

    /**
     * Writes a struct/map field whose value is {@code expr |> case do ... end}.
     */
    static void writePipeCaseField(ElixirWriter writer, String fieldName, String expr, List<String[]> cases) {
        writer.write("$L:", fieldName);
        writer.indent();
        writer.write("$L", expr);
        writer.write("|> case do");
        writer.indent();
        for (String[] branch : cases) {
            writer.write("$L -> $L", branch[0], branch[1]);
        }
        writer.dedent();
        writer.write("end");
        writer.dedent();
    }

    /**
     * Writes a standalone {@code expr |> case do ... end} block.
     */
    static void writePipeCase(ElixirWriter writer, String expr, List<String[]> cases) {
        writer.write("$L", expr);
        writer.write("|> case do");
        writer.indent();
        for (String[] branch : cases) {
            writer.write("$L -> $L", branch[0], branch[1]);
        }
        writer.dedent();
        writer.write("end");
    }

    /**
     * Writes a long function head split across multiple lines.
     */
    static void breakFunctionHead(ElixirWriter writer, String keyword, String name, List<String> args) {
        if (args.isEmpty()) {
            writer.write("$L $L() do", keyword, name);
            return;
        }
        writer.write("$L $L(", keyword, name);
        writer.indent();
        for (int i = 0; i < args.size(); i++) {
            if (i < args.size() - 1) {
                writer.write("$L,", args.get(i));
            } else {
                writer.write("$L", args.get(i));
            }
        }
        writer.dedent();
        writer.write(") do");
    }

    /**
     * Writes a struct type block with mix-format-style closing brace alignment.
     */
    static void writeStructureType(ElixirWriter writer, List<String> fieldLines) {
        writer.write("@type t :: %__MODULE__{");
        writer.indent();
        writer.indent();
        writer.indent();
        writer.indent();
        for (int i = 0; i < fieldLines.size(); i++) {
            if (i < fieldLines.size() - 1) {
                writer.write("$L,", fieldLines.get(i));
            } else {
                writer.write("$L", fieldLines.get(i));
            }
        }
        writer.dedent();
        writer.write("}");
        writer.dedent();
        writer.dedent();
        writer.dedent();
    }
}
