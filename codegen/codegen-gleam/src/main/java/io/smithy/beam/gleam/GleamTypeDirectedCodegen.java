package io.smithy.beam.gleam;

import io.smithy.beam.core.BeamCodegenKind;
import io.smithy.beam.core.BeamGleamLayout;
import io.smithy.beam.core.BeamHttpBindings;
import io.smithy.beam.core.BeamSettings;
import software.amazon.smithy.codegen.core.SymbolProvider;
import software.amazon.smithy.codegen.core.WriterDelegator;
import software.amazon.smithy.codegen.core.directed.CreateContextDirective;
import software.amazon.smithy.codegen.core.directed.CreateSymbolProviderDirective;
import software.amazon.smithy.codegen.core.directed.CustomizeDirective;
import software.amazon.smithy.codegen.core.directed.DirectedCodegen;
import software.amazon.smithy.codegen.core.directed.GenerateEnumDirective;
import software.amazon.smithy.codegen.core.directed.GenerateErrorDirective;
import software.amazon.smithy.codegen.core.directed.GenerateIntEnumDirective;
import software.amazon.smithy.codegen.core.directed.GenerateResourceDirective;
import software.amazon.smithy.codegen.core.directed.GenerateServiceDirective;
import software.amazon.smithy.codegen.core.directed.GenerateStructureDirective;
import software.amazon.smithy.codegen.core.directed.GenerateUnionDirective;
import software.amazon.smithy.model.shapes.ServiceShape;

/** DirectedCodegen implementation for the Gleam types generator. */
final class GleamTypeDirectedCodegen
    implements DirectedCodegen<GleamContext, BeamSettings, GleamIntegration> {

  @Override
  public SymbolProvider createSymbolProvider(
      CreateSymbolProviderDirective<BeamSettings> directive) {
    ServiceShape service = directive.service();
    String ns = service.getId().getNamespace();
    BeamSettings settings = directive.settings();
    BeamGleamLayout layout = new BeamGleamLayout(settings, ns, service);
    String definitionFile = layout.typesModuleFile();
    return SymbolProvider.cache(
        new GleamSymbolProvider(
            settings,
            directive.model(),
            directive.service(),
            definitionFile,
            layout.typesModuleName(),
            BeamCodegenKind.TYPES));
  }

  @Override
  public GleamContext createContext(
      CreateContextDirective<BeamSettings, GleamIntegration> directive) {
    ServiceShape service = directive.service();
    BeamHttpBindings httpBindings = BeamHttpBindings.from(directive.model());
    String ns = service.getId().getNamespace();
    BeamSettings settings = directive.settings();
    BeamGleamLayout layout = new BeamGleamLayout(settings, ns, service);
    String definitionFile = layout.typesModuleFile();
    String moduleName = layout.typesModuleName();
    return GleamContext.forTypes(
        directive.model(),
        directive.settings(),
        directive.symbolProvider(),
        directive.fileManifest(),
        new WriterDelegator<>(
            directive.fileManifest(), directive.symbolProvider(), GleamWriter.factory()),
        directive.integrations(),
        service,
        httpBindings,
        moduleName,
        definitionFile);
  }

  @Override
  public void customizeBeforeShapeGeneration(
      CustomizeDirective<GleamContext, BeamSettings> directive) {}

  @Override
  public void customizeBeforeIntegrations(
      CustomizeDirective<GleamContext, BeamSettings> directive) {}

  @Override
  public void customizeAfterIntegrations(
      CustomizeDirective<GleamContext, BeamSettings> directive) {}

  @Override
  public void generateService(GenerateServiceDirective<GleamContext, BeamSettings> directive) {}

  @Override
  public void generateResource(GenerateResourceDirective<GleamContext, BeamSettings> directive) {}

  @Override
  public void generateEnumShape(GenerateEnumDirective<GleamContext, BeamSettings> directive) {}

  @Override
  public void generateIntEnumShape(
      GenerateIntEnumDirective<GleamContext, BeamSettings> directive) {}

  @Override
  public void generateUnion(GenerateUnionDirective<GleamContext, BeamSettings> directive) {}

  @Override
  public void generateStructure(
      GenerateStructureDirective<GleamContext, BeamSettings> directive) {}

  @Override
  public void generateError(GenerateErrorDirective<GleamContext, BeamSettings> directive) {}
}
