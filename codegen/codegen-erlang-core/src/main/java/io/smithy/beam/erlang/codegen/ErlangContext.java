package io.smithy.beam.erlang.codegen;

import java.util.List;
import software.amazon.smithy.build.FileManifest;
import software.amazon.smithy.codegen.core.CodegenContext;
import software.amazon.smithy.codegen.core.SymbolProvider;
import software.amazon.smithy.codegen.core.WriterDelegator;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.shapes.ServiceShape;
import software.amazon.smithy.model.transform.ModelTransformer;

/**
 * Codegen context for all Erlang codegen plugins.
 *
 * <p>Implements {@link CodegenContext} parameterised on
 * {@link ErlangSettings}, {@link ErlangWriter}, and {@link ErlangIntegration}.
 * Each accessor is a one-liner delegate to the corresponding record component.
 *
 * <p>In addition to the mandatory {@code CodegenContext} accessors, this
 * context exposes {@link #modelTransformer()} and {@link #service()} as
 * Erlang-specific conveniences so that codegen implementations do not need
 * to re-derive them from the model on every call.
 *
 * @param model              the Smithy model being code-generated
 * @param modelTransformer   the transformer used during code generation
 * @param settings           the resolved Erlang settings for this invocation
 * @param symbolProvider     the symbol provider for shape-to-Erlang mapping
 * @param fileManifest       the file manifest to write generated files into
 * @param writerDelegator    the writer delegator that manages per-file writers
 * @param integrations       the ordered list of active Erlang integrations
 * @param service            the service shape being code-generated
 */
public record ErlangContext(
        Model model,
        ModelTransformer modelTransformer,
        ErlangSettings settings,
        SymbolProvider symbolProvider,
        FileManifest fileManifest,
        WriterDelegator<ErlangWriter> writerDelegator,
        List<ErlangIntegration> integrations,
        ServiceShape service
) implements CodegenContext<ErlangSettings, ErlangWriter, ErlangIntegration> {

    // All methods below are satisfied by the compact record; they are listed
    // explicitly to satisfy the CodegenContext interface contract.

    @Override
    public Model model() {
        return model;
    }

    @Override
    public ErlangSettings settings() {
        return settings;
    }

    @Override
    public SymbolProvider symbolProvider() {
        return symbolProvider;
    }

    @Override
    public FileManifest fileManifest() {
        return fileManifest;
    }

    @Override
    public WriterDelegator<ErlangWriter> writerDelegator() {
        return writerDelegator;
    }

    @Override
    public List<ErlangIntegration> integrations() {
        return integrations;
    }
}
