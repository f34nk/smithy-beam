package io.smithy.beam.elixir;

import software.amazon.smithy.codegen.core.SymbolWriter;
import software.amazon.smithy.utils.CodeSection;

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

    public void pushGeneratedDocumentationSection() {
        pushState((CodeSection) new ElixirWriterSections.GeneratedDocumentation());
    }

    public void pushModuleHeaderSection() {
        pushState((CodeSection) new ElixirWriterSections.ModuleHeader());
    }

    public void pushDependenciesSection() {
        pushState((CodeSection) new ElixirWriterSections.Dependencies());
    }

    public void pushProtocolHookSection() {
        pushState((CodeSection) new ElixirWriterSections.ProtocolHook());
    }

    public void pushTransportHookSection() {
        pushState((CodeSection) new ElixirWriterSections.TransportHook());
    }

    public void pushOperationBodySection() {
        pushState((CodeSection) new ElixirWriterSections.OperationBody());
    }
}
