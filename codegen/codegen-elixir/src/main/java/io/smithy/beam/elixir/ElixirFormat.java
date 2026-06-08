package io.smithy.beam.elixir;

import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Shared formatting helpers for generated Elixir output.
 *
 * <p>Encodes mix-format-style layout rules used across emitters so generated code
 * matches idiomatic Elixir without a post-processing pass.
 */
final class ElixirFormat {

    static final int SPEC_LINE_LIMIT = 98;
    static final int HEREDOC_BODY_INDENT_LEVELS = 2;
    static final int DEFSTRUCT_MULTILINE_THRESHOLD = 4;
    static final int IF_IN_LIST_SINGLE_LINE_LIMIT = 72;
    private static final Pattern STRUCT_PATTERN = Pattern.compile("(%[^{]+)\\{([^}]+)\\}");

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
     * Writes a multi-line {@code @callback} with each parameter on its own indented line.
     *
     * <p>Call when the writer is already at module-body indent. Does not add a trailing blank line.
     */
    static void writeCallbackSpec(ElixirWriter writer, String name, List<String> params, String returnType) {
        writer.write("@callback $L(", name);
        for (int i = 0; i < 6; i++) {
            writer.indent();
        }
        for (int i = 0; i < params.size(); i++) {
            if (i < params.size() - 1) {
                writer.write("$L,", params.get(i));
            } else {
                writer.write("$L", params.get(i));
            }
        }
        writer.dedent();
        writer.write(") ::");
        writer.indent();
        writer.write("$L", returnType);
        for (int i = 0; i < 6; i++) {
            writer.dedent();
        }
    }

    /**
     * Writes a single-line or heredoc {@code @doc} / {@code @moduledoc} attribute.
     *
     * <p>For multiline text, opens the heredoc, writes the body via {@link #writeHeredocBody},
     * then closes the heredoc. Call when the writer is already at module-body indent.
     */
    static void writeDocAttribute(ElixirWriter writer, String attribute, String doc) {
        if (!doc.contains("\n")) {
            writer.write("$L \"$L\"", attribute, escapeElixirString(doc));
            return;
        }
        writer.openBlock("$L \"\"\"", attribute);
        writeHeredocBody(writer, Arrays.asList(doc.split("\n", -1)));
        writer.closeBlock("\"\"\"");
    }

    /**
     * Writes heredoc body lines with four-space-relative indent inside {@code @moduledoc} /
     * {@code @doc}. The heredoc opener must already be written; call before the closing
     * {@code """}.
     */
    static void writeHeredocBody(ElixirWriter writer, List<String> lines) {
        for (int i = 0; i < HEREDOC_BODY_INDENT_LEVELS; i++) {
            writer.indent();
        }
        for (String line : lines) {
            writer.write("$L", line);
        }
        for (int i = 0; i < HEREDOC_BODY_INDENT_LEVELS; i++) {
            writer.dedent();
        }
    }

    /**
     * Writes {@code defstruct} on one line or as a multi-line list when the field count exceeds
     * {@link #DEFSTRUCT_MULTILINE_THRESHOLD}.
     */
    static void writeDefstruct(ElixirWriter writer, List<String> fields) {
        if (fields.size() <= DEFSTRUCT_MULTILINE_THRESHOLD) {
            writer.write("defstruct [$L]", String.join(", ", fields));
            return;
        }
        writer.write("defstruct [");
        writer.indent();
        for (int i = 0; i < fields.size(); i++) {
            if (i < fields.size() - 1) {
                writer.write("$L,", fields.get(i));
            } else {
                writer.write("$L", fields.get(i));
            }
        }
        writer.dedent();
        writer.write("]");
    }

    /**
     * Writes keyword-style {@code defexception} with each field on its own aligned line.
     */
    static void writeDefexception(ElixirWriter writer, List<String> fields) {
        if (fields.isEmpty()) {
            writer.write("defexception []");
            return;
        }
        writer.write("defexception $L,", fields.get(0));
        for (int i = 1; i < fields.size(); i++) {
            if (i < fields.size() - 1) {
                writer.write("             $L,", fields.get(i));
            } else {
                writer.write("             $L", fields.get(i));
            }
        }
    }

    /**
     * Writes a union {@code @type} with aligned {@code |} members under the type name.
     */
    static void writeUnionType(ElixirWriter writer, String name, List<String> variants) {
        if (variants.size() <= 2) {
            writer.write("@type $L :: $L", name, String.join(" | ", variants));
            return;
        }
        writer.write("@type $L ::", name);
        for (int i = 0; i < 4; i++) {
            writer.indent();
        }
        for (int i = 0; i < variants.size(); i++) {
            String prefix = (i == 0) ? "" : "| ";
            writer.write("$L$L", prefix, variants.get(i));
        }
        for (int i = 0; i < 4; i++) {
            writer.dedent();
        }
    }

    /**
     * Closes a {@code defmodule} without emitting a trailing blank line before {@code end}.
     *
     * <p>Call once to balance the indent opened when the module body started.
     */
    static void writeModuleEnd(ElixirWriter writer) {
        writer.dedent();
        writer.write("end");
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
     * Writes an optional list entry using an {@code if(...)} expression.
     */
    static void writeIfInList(ElixirWriter writer, String condition, String doExpr) {
        writeIfInList(writer, condition, doExpr, false);
    }

    /**
     * Writes an optional list entry using an {@code if(...)} expression.
     *
     * <p>Uses a single-line form when {@code forceSingleLine} is true or the full expression
     * fits within {@link #IF_IN_LIST_SINGLE_LINE_LIMIT} characters.
     */
    static void writeIfInList(ElixirWriter writer, String condition, String doExpr, boolean forceSingleLine) {
        String singleLine = "if(" + condition + ", do: " + doExpr + ", else: nil)";
        if (forceSingleLine || singleLine.length() <= IF_IN_LIST_SINGLE_LINE_LIMIT) {
            writer.write("$L", singleLine);
            return;
        }
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
        writePipeCaseField(writer, fieldName, expr, cases, false);
    }

    /**
     * Writes a struct/map field whose value is {@code expr |> case do ... end}.
     */
    static void writePipeCaseField(
            ElixirWriter writer, String fieldName, String expr, List<String[]> cases, boolean blankBetweenBranches) {
        writer.write("$L:", fieldName);
        writer.indent();
        writePipeCase(writer, expr, cases, blankBetweenBranches);
        writer.dedent();
    }

    /**
     * Writes a standalone {@code expr |> case do ... end} block.
     */
    static void writePipeCase(ElixirWriter writer, String expr, List<String[]> cases) {
        writePipeCase(writer, expr, cases, false);
    }

    /**
     * Writes a standalone {@code expr |> case do ... end} block.
     */
    static void writePipeCase(ElixirWriter writer, String expr, List<String[]> cases, boolean blankBetweenBranches) {
        writer.write("$L", expr);
        writer.write("|> case do");
        writer.indent();
        for (int i = 0; i < cases.size(); i++) {
            if (blankBetweenBranches && i > 0) {
                writer.write("");
            }
            writer.write("$L -> $L", cases.get(i)[0], cases.get(i)[1]);
        }
        writer.dedent();
        writer.write("end");
    }

    /**
     * Writes a long function head split across multiple lines.
     */
    static void breakFunctionHead(ElixirWriter writer, String keyword, String name, List<String> args) {
        breakFunctionHead(writer, keyword, name, args, null);
    }

    /**
     * Writes a long function head split across multiple lines, with an optional {@code when} clause.
     *
     * <p>When an argument is a {@code %Struct{...}} pattern with multiple fields, the struct
     * fields are broken onto separate lines.
     */
    static void breakFunctionHead(
            ElixirWriter writer, String keyword, String name, List<String> args, String whenClause) {
        if (args.isEmpty()) {
            if (whenClause == null) {
                writer.write("$L $L() do", keyword, name);
            } else {
                writer.write("$L $L() $L do", keyword, name, whenClause);
            }
            return;
        }
        if (args.size() == 1 && isStructPatternArg(args.get(0))) {
            breakStructPatternFunctionHead(writer, keyword, name, args.get(0), whenClause);
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
        if (whenClause == null) {
            writer.write(") do");
        } else {
            writer.write(") $L do", whenClause);
        }
    }

    /**
     * Writes a {@code defp} head with a multi-line {@code do:} body (for example expanded {@code fn}).
     */
    static void writeDefpDoClause(ElixirWriter writer, String head, List<String> bodyLines) {
        writer.write("$L,", head);
        writer.write("  do:");
        writer.indent();
        for (String line : bodyLines) {
            writer.write("$L", line);
        }
        writer.dedent();
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

    private static boolean isStructPatternArg(String arg) {
        if (!arg.startsWith("%") || !arg.contains("{") || !arg.contains("}")) {
            return false;
        }
        Matcher matcher = STRUCT_PATTERN.matcher(arg);
        if (!matcher.matches()) {
            return false;
        }
        return matcher.group(2).contains(",");
    }

    private static void breakStructPatternFunctionHead(
            ElixirWriter writer, String keyword, String name, String pattern, String whenClause) {
        Matcher matcher = STRUCT_PATTERN.matcher(pattern);
        matcher.matches();
        String structHead = matcher.group(1);
        String[] fields = matcher.group(2).split(",\\s*");
        writer.write("$L $L($L", keyword, name, structHead + "{");
        writer.indent();
        for (int i = 0; i < fields.length; i++) {
            if (i < fields.length - 1) {
                writer.write("$L,", fields[i]);
            } else {
                writer.write("$L", fields[i]);
            }
        }
        writer.dedent();
        if (whenClause == null) {
            writer.write("}) do");
        } else {
            writer.write("}) $L do", whenClause);
        }
    }

    private static String escapeElixirString(String doc) {
        return doc.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
