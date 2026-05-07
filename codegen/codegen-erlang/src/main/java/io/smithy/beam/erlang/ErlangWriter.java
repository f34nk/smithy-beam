package io.smithy.beam.erlang;

import software.amazon.smithy.codegen.core.SymbolWriter;

/**
 * Code writer for Erlang .hrl files.
 *
 * There is no import header to prepend; toString() returns the raw content.
 * Use ErlangWriter.factory() when constructing a WriterDelegator.
 */
public final class ErlangWriter extends SymbolWriter<ErlangWriter, ErlangImports> {

    public ErlangWriter(String filename) {
        super(new ErlangImports());
        trimBlankLines();
        trimTrailingSpaces();
    }

    @Override
    public String toString() {
        // No import section to prepend for .hrl files.
        return super.toString();
    }

    public static Factory<ErlangWriter> factory() {
        return (filename, namespace) -> new ErlangWriter(filename);
    }
}
