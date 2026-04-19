package io.smithy.beam.elixir.codegen;

import java.util.List;
import software.amazon.smithy.build.FileManifest;
import software.amazon.smithy.codegen.core.CodegenContext;
import software.amazon.smithy.codegen.core.SymbolProvider;
import software.amazon.smithy.codegen.core.WriterDelegator;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.shapes.ServiceShape;
import software.amazon.smithy.model.transform.ModelTransformer;

/**
 * Codegen context for all Elixir generation passes (client and server).
 *
 * <p>Implements {@link CodegenContext} via record components whose names align
 * exactly with the interface's accessor methods.
 */
public record ElixirContext(
        Model model,
        ModelTransformer modelTransformer,
        ElixirSettings settings,
        SymbolProvider symbolProvider,
        FileManifest fileManifest,
        WriterDelegator<ElixirWriter> writerDelegator,
        List<ElixirIntegration> integrations,
        ServiceShape service
) implements CodegenContext<ElixirSettings, ElixirWriter, ElixirIntegration> {
}
