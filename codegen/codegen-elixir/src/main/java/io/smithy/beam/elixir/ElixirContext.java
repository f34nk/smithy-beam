package io.smithy.beam.elixir;

import io.smithy.beam.core.BeamHttpBindings;
import io.smithy.beam.core.BeamProtocolCodegen;
import io.smithy.beam.core.BeamSettings;
import software.amazon.smithy.build.FileManifest;
import software.amazon.smithy.codegen.core.CodegenContext;
import software.amazon.smithy.codegen.core.SymbolProvider;
import software.amazon.smithy.codegen.core.WriterDelegator;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.shapes.ServiceShape;

import java.util.List;

/**
 * Provides all context needed during Elixir code generation.
 *
 * Created once by ElixirDirectedCodegen.createContext(). The moduleName field
 * carries the UpperCamelCase Elixir module name (e.g. "BasicTypes") used to
 * open and close the defmodule block.
 */
public record ElixirContext(
        Model model,
        BeamSettings settings,
        SymbolProvider symbolProvider,
        FileManifest fileManifest,
        WriterDelegator<ElixirWriter> writerDelegator,
        List<ElixirIntegration> integrations,
        ServiceShape service,
        BeamHttpBindings httpBindings,
        BeamProtocolCodegen protocolCodegen,
        String moduleName,
        String definitionFile) implements CodegenContext<BeamSettings, ElixirWriter, ElixirIntegration> {
}
