package io.smithy.beam.erlang.codegen;

import java.util.List;
import software.amazon.smithy.codegen.core.CodegenContext;
import software.amazon.smithy.codegen.core.SymbolProvider;
import software.amazon.smithy.codegen.core.WriterDelegator;
import software.amazon.smithy.build.FileManifest;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.shapes.ServiceShape;
import software.amazon.smithy.model.transform.ModelTransformer;

/**
 * Codegen context for all Erlang generation passes (client and server).
 *
 * <p>Implements {@link CodegenContext} via record components whose names align
 * exactly with the interface's accessor methods.
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
}
