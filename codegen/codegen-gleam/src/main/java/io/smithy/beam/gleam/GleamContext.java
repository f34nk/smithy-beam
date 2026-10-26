package io.smithy.beam.gleam;

import io.beam.lang.gleam.CustomType;
import io.beam.lang.gleam.DocComment;
import io.beam.lang.gleam.Import;
import io.beam.lang.gleam.TypeAlias;
import io.smithy.beam.core.BeamHttpBindings;
import io.smithy.beam.core.BeamProtocolCodegen;
import io.smithy.beam.core.BeamSettings;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
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
    String definitionFile,
    GleamTypesBuilder typesBuilder)
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
        definitionFile,
        new GleamTypesBuilder());
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
        definitionFile,
        new GleamTypesBuilder());
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
        definitionFile,
        new GleamTypesBuilder());
  }

  /** Mutable accumulator for types-module IR. */
  public static final class GleamTypesBuilder {
    private DocComment moduleDocOrNull;
    private final Set<String> importKeys = new LinkedHashSet<>();
    private final List<Import> imports = new ArrayList<>();
    private final List<TypeAlias> typeAliases = new ArrayList<>();
    private final List<CustomType> customTypes = new ArrayList<>();

    public void setModuleDoc(DocComment doc) {
      this.moduleDocOrNull = doc;
    }

    public void addImport(Import importStmt) {
      String key = importStmt.modulePath() + "|" + String.join(",", importStmt.typeNames());
      if (importKeys.add(key)) {
        imports.add(importStmt);
      }
    }

    public void addTypeAlias(TypeAlias alias) {
      typeAliases.add(alias);
    }

    public void addCustomType(CustomType customType) {
      customTypes.add(customType);
    }

    public DocComment moduleDocOrNull() {
      return moduleDocOrNull;
    }

    public List<Import> imports() {
      return List.copyOf(imports);
    }

    public List<TypeAlias> typeAliases() {
      return List.copyOf(typeAliases);
    }

    public List<CustomType> customTypes() {
      return List.copyOf(customTypes);
    }
  }
}
