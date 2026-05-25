package io.smithy.beam.erlang;

final class ErlangStringLiterals {

    private ErlangStringLiterals() {}

    /** Escapes a Java string for use inside an Erlang binary literal. */
    static String escapeBinaryContents(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
