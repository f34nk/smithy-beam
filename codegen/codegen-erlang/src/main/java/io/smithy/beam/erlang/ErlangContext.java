package io.smithy.beam.erlang;

import io.smithy.beam.core.BeamSettings;
import software.amazon.smithy.build.FileManifest;
import software.amazon.smithy.codegen.core.CodegenContext;
import software.amazon.smithy.codegen.core.SymbolProvider;
import software.amazon.smithy.codegen.core.WriterDelegator;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.shapes.ServiceShape;

import java.util.List;

/**
 * Provides all context needed during Erlang code generation.
 *
 * Created once by ErlangDirectedCodegen.createContext() and passed to every
 * generate* method via the directive. Add new getters here when additional
 * shared state is required by the generator.
 */
public record ErlangContext(
        Model model,
        BeamSettings settings,
        SymbolProvider symbolProvider,
        FileManifest fileManifest,
        WriterDelegator<ErlangWriter> writerDelegator,
        List<ErlangIntegration> integrations,
        ServiceShape service
) implements CodegenContext<BeamSettings, ErlangWriter, ErlangIntegration> {
}
