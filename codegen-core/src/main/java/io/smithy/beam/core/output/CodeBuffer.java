package io.smithy.beam.core.output;

/**
 * Mutable string accumulator for generated source text.
 */
public final class CodeBuffer {

    private final StringBuilder sb = new StringBuilder();
    private int indentLevel;

    public CodeBuffer append(String s) {
        sb.append(s);
        return this;
    }

    public CodeBuffer appendLine(String s) {
        indent();
        sb.append(s).append('\n');
        return this;
    }

    public CodeBuffer appendBlankLine() {
        sb.append('\n');
        return this;
    }

    public CodeBuffer indent(int n) {
        indentLevel = Math.max(0, indentLevel + n);
        return this;
    }

    public CodeBuffer dedent(int n) {
        indentLevel = Math.max(0, indentLevel - n);
        return this;
    }

    private void indent() {
        sb.append("    ".repeat(indentLevel));
    }

    @Override
    public String toString() {
        return sb.toString();
    }
}
