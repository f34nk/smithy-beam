package io.smithy.beam.gleam;

import io.smithy.beam.core.BeamHttpBindings;
import io.smithy.beam.core.BeamProtocolCodegen;
import io.smithy.beam.core.BeamSettings;
import java.util.List;
import software.amazon.smithy.build.FileManifest;
import software.amazon.smithy.codegen.core.CodegenContext;
import software.amazon.smithy.codegen.core.SymbolProvider;
import software.amazon.smithy.codegen.core.WriterDelegator;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.shapes.ServiceShape;
import software.amazon.smithy.model.shapes.ShapeId;

/**
 * Provides all context needed during Gleam code generation.
 *
 * <p>Created once by each DirectedCodegen {@code createContext()} and passed to every generate*
 * method via the directive. The {@code moduleName} is the primary module label for the current
 * pass. The {@code definitionFile} is the relative path of that pass primary output file.
 */
public record GleamContext(
    Model model,
    BeamSettings settings,
    SymbolProvider symbolProvider,
    FileManifest fileManifest,
    WriterDelegator<GleamWriter> writerDelegator,
    List<GleamIntegration> integrations,
    ServiceShape service,
    BeamHttpBindings httpBindings,
    BeamProtocolCodegen protocolCodegen,
    ShapeId resolvedProtocolTraitId,
    String moduleName,
    String definitionFile)
    implements CodegenContext<BeamSettings, GleamWriter, GleamIntegration> {

  public static GleamContext forTypes(
      Model model,
      BeamSettings settings,
      SymbolProvider symbolProvider,
      FileManifest fileManifest,
      WriterDelegator<GleamWriter> writerDelegator,
      List<GleamIntegration> integrations,
      ServiceShape service,
      BeamHttpBindings httpBindings,
      String moduleName,
      String definitionFile) {
    return new GleamContext(
        model,
        settings,
        symbolProvider,
        fileManifest,
        writerDelegator,
        integrations,
        service,
        httpBindings,
        null,
        null,
        moduleName,
        definitionFile);
  }

  public static GleamContext forClient(
      Model model,
      BeamSettings settings,
      SymbolProvider symbolProvider,
      FileManifest fileManifest,
      WriterDelegator<GleamWriter> writerDelegator,
      List<GleamIntegration> integrations,
      ServiceShape service,
      BeamHttpBindings httpBindings,
      BeamProtocolCodegen protocolCodegen,
      ShapeId resolvedProtocolTraitId,
      String moduleName,
      String definitionFile) {
    return new GleamContext(
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
        definitionFile);
  }

  public static GleamContext forServer(
      Model model,
      BeamSettings settings,
      SymbolProvider symbolProvider,
      FileManifest fileManifest,
      WriterDelegator<GleamWriter> writerDelegator,
      List<GleamIntegration> integrations,
      ServiceShape service,
      BeamHttpBindings httpBindings,
      BeamProtocolCodegen protocolCodegen,
      ShapeId resolvedProtocolTraitId,
      String moduleName,
      String definitionFile) {
    return new GleamContext(
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
        definitionFile);
  }
}
