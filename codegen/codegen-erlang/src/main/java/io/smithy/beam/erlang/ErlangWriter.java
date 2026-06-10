package io.smithy.beam.erlang;

import software.amazon.smithy.codegen.core.SymbolWriter;
import software.amazon.smithy.utils.CodeSection;

/**
 * Code writer for Erlang generated sources.
 *
 * Include lines are written in the {@link ErlangWriterSections.Dependencies} section
 * after {@link ErlangWriterSections.ModuleHeader}, not prepended to the file.
 * Use ErlangWriter.factory() when constructing a WriterDelegator.
 */
public final class ErlangWriter extends SymbolWriter<ErlangWriter, ErlangImports> {

    public ErlangWriter(String filename) {
        super(new ErlangImports());
        setIndentText("    ");
        trimBlankLines();
        trimTrailingSpaces();
    }

    public static Factory<ErlangWriter> factory() {
        return (filename, namespace) -> new ErlangWriter(filename);
    }

    public void pushGeneratedDocumentationSection() {
        pushState((CodeSection) new ErlangWriterSections.GeneratedDocumentation());
    }

    public void pushModuleHeaderSection() {
        pushState((CodeSection) new ErlangWriterSections.ModuleHeader());
    }

    public void pushDependenciesSection() {
        pushState((CodeSection) new ErlangWriterSections.Dependencies());
    }

    public void pushProtocolHookSection() {
        pushState((CodeSection) new ErlangWriterSections.ProtocolHook());
    }

    public void pushTransportHookSection() {
        pushState((CodeSection) new ErlangWriterSections.TransportHook());
    }

    public void pushOperationBodySection() {
        pushState((CodeSection) new ErlangWriterSections.OperationBody());
    }
}
