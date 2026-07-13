package io.smithy.beam.elixir;

import io.smithy.beam.core.BeamHttpBindings;
import io.smithy.beam.core.BeamProtocolCodegen;
import io.smithy.beam.core.BeamProtocolSupport;
import io.smithy.beam.core.BeamSettings;
import io.smithy.beam.ir.elixir.ExFunction;
import io.smithy.beam.ir.elixir.ExModuleEntry;
import io.smithy.beam.ir.elixir.ExPreambleEntry;
import java.util.ArrayList;
import java.util.List;
import software.amazon.smithy.build.FileManifest;
import software.amazon.smithy.codegen.core.CodegenContext;
import software.amazon.smithy.codegen.core.SymbolProvider;
import software.amazon.smithy.codegen.core.WriterDelegator;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.shapes.ServiceShape;
import software.amazon.smithy.model.shapes.ShapeId;

/**
 * Provides all context needed during Elixir code generation.
 *
 * <p>Created once by ElixirDirectedCodegen.createContext(). The moduleName field carries the
 * UpperCamelCase Elixir module name (e.g. "BasicTypes") used to open and close the defmodule block.
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
    ShapeId resolvedProtocolTraitId,
    String moduleName,
    String definitionFile,
    List<ExPreambleEntry> typesPreambleEntries,
    List<ExModuleEntry> typesEntries,
    List<ExFunction> typesFunctions,
    ElixirClientModuleBuilder clientModuleBuilderOrNull,
    ElixirBehaviourModuleBuilder behaviourModuleBuilderOrNull,
    ElixirServerModuleBuilder serverModuleBuilderOrNull)
    implements CodegenContext<BeamSettings, ElixirWriter, ElixirIntegration> {

  public ElixirContext(
      Model model,
      BeamSettings settings,
      SymbolProvider symbolProvider,
      FileManifest fileManifest,
      WriterDelegator<ElixirWriter> writerDelegator,
      List<ElixirIntegration> integrations,
      ServiceShape service,
      BeamHttpBindings httpBindings,
      BeamProtocolCodegen protocolCodegen,
      ShapeId resolvedProtocolTraitId,
      String moduleName,
      String definitionFile) {
    this(
        model,
        settings,
        symbolProvider,
        fileManifest,
        writerDelegator,
        integrations,
        service,
        httpBindings,
        protocolCodegen,
        resolvedProtocolTraitId,
        moduleName,
        definitionFile,
        new ArrayList<>(),
        new ArrayList<>(),
        new ArrayList<>(),
        null,
        null,
        null);
  }

  public void addTypesPreambleEntry(ExPreambleEntry entry) {
    typesPreambleEntries.add(entry);
  }

  public void addTypesEntry(ExModuleEntry entry) {
    typesEntries.add(entry);
  }

  public void addTypesFunction(ExFunction function) {
    typesFunctions.add(function);
  }

  public boolean hasWireProtocol() {
    return BeamProtocolSupport.hasWireCodegen(
        resolvedProtocolTraitId(), protocolCodegen(), integrations());
  }
}
