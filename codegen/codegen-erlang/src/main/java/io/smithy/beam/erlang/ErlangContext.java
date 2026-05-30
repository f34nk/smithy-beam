package io.smithy.beam.erlang;

import io.smithy.beam.core.BeamHttpBindings;
import io.smithy.beam.core.BeamProtocolCodegen;
import io.smithy.beam.core.BeamSettings;
import software.amazon.smithy.build.FileManifest;
import software.amazon.smithy.codegen.core.CodegenContext;
import software.amazon.smithy.codegen.core.SymbolProvider;
import software.amazon.smithy.codegen.core.WriterDelegator;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.shapes.ServiceShape;
import software.amazon.smithy.model.shapes.ShapeId;

import java.util.List;

/**
 * Provides all context needed during Erlang code generation.
 *
 * Created once by each DirectedCodegen {@code createContext()} and passed to every
 * generate* method via the directive. The {@code moduleName} is the primary module
 * or namespace label for the current pass (resolved module prefix for types headers,
 * or the {@code -module} name for client and server modules). The {@code definitionFile}
 * is the relative path of that pass primary output file, matching the symbol provider
 * definition file so writers relativize consistently.
 */
public record ErlangContext(
        Model model,
        BeamSettings settings,
        SymbolProvider symbolProvider,
        FileManifest fileManifest,
        WriterDelegator<ErlangWriter> writerDelegator,
        List<ErlangIntegration> integrations,
        ServiceShape service,
        BeamHttpBindings httpBindings,
        BeamProtocolCodegen protocolCodegen,
        ShapeId resolvedProtocolTraitId,
        String moduleName,
        String definitionFile)
        implements CodegenContext<BeamSettings, ErlangWriter, ErlangIntegration> {
}
