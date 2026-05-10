package io.smithy.beam.elixir;

import software.amazon.smithy.codegen.core.SymbolWriter;

/**
 * Code writer for Elixir .ex type files.
 *
 * The writer accumulates a defmodule block opened by
 * customizeBeforeShapeGeneration
 * and closed by customizeAfterIntegrations. All generate* methods append into
 * the same open writer instance via WriterDelegator.
 */
public final class ElixirWriter extends SymbolWriter<ElixirWriter, ElixirImports> {

    public ElixirWriter(String filename, String namespace) {
        super(new ElixirImports());
        setRelativizeSymbols(namespace);
        trimBlankLines();
        trimTrailingSpaces();
    }

    @Override
    public String toString() {
        return super.toString();
    }

    public static Factory<ElixirWriter> factory(String namespace) {
        return (filename, ns) -> new ElixirWriter(filename, namespace);
    }
}
