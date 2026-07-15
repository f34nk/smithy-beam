package io.smithy.beam.elixir;

import io.beam.dsl.elixir.Function;
import io.beam.dsl.elixir.Moduledoc;
import io.beam.dsl.elixir.TypesModule;
import io.smithy.beam.core.BeamHttpBindings;
import io.smithy.beam.core.BeamProtocolCodegen;
import io.smithy.beam.core.BeamProtocolSupport;
import io.smithy.beam.core.BeamSettings;
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
    List<ElixirTypesEntry> typesEntries,
    List<Function> typesFunctions,
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
        null,
        null,
        null);
  }

  public void addTypesModuledoc(Moduledoc moduledoc) {
    typesEntries.add(new ElixirTypesModuledocEntry(moduledoc));
  }

  public void addTypesRootLines(List<String> lines) {
    for (String line : lines) {
      typesEntries.add(new ElixirTypesRootLine(line));
    }
  }

  public void addTypesRootLine(String line) {
    typesEntries.add(new ElixirTypesRootLine(line));
  }

  public void addTypesStructNested(TypesModule typesModule) {
    typesEntries.add(new ElixirTypesStructNested(typesModule));
  }

  public void addTypesEmbeddedNested(ElixirTypesEmbeddedNested nested) {
    typesEntries.add(nested);
  }

  public void addTypesFunction(Function function) {
    typesFunctions.add(function);
  }

  public boolean hasWireProtocol() {
    return BeamProtocolSupport.hasWireCodegen(
        resolvedProtocolTraitId(), protocolCodegen(), integrations());
  }
}
