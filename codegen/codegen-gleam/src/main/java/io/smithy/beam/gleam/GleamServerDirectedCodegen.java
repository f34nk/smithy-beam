package io.smithy.beam.gleam;

import io.smithy.beam.core.BeamCodegenKind;
import io.smithy.beam.core.BeamEdition;
import io.smithy.beam.core.BeamGleamLayout;
import io.smithy.beam.core.BeamHttpBindings;
import io.smithy.beam.core.BeamProtocolCodegen;
import io.smithy.beam.core.BeamProtocolCodegenFactory;
import io.smithy.beam.core.BeamProtocolResolver;
import io.smithy.beam.core.BeamSettings;
import java.util.Optional;
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
import software.amazon.smithy.model.shapes.ShapeId;

/**
 * Server DirectedCodegen. Types are emitted by {@link GleamTypeGeneration} before this runs; shape
 * generate* methods stay empty and {@link #generateService} writes behaviour, impl, server, and
 * router stubs.
 */
final class GleamServerDirectedCodegen
    implements DirectedCodegen<GleamContext, BeamSettings, GleamIntegration> {

  @Override
  public SymbolProvider createSymbolProvider(
      CreateSymbolProviderDirective<BeamSettings> directive) {
    ServiceShape service = directive.service();
    String ns = service.getId().getNamespace();
    BeamSettings settings = directive.settings();
    BeamGleamLayout layout = new BeamGleamLayout(settings, ns, service);
    return SymbolProvider.cache(
        new GleamSymbolProvider(
            settings,
            directive.model(),
            service,
            layout.serverModuleFile(),
            layout.serverModuleName(),
            BeamCodegenKind.SERVER));
  }

  @Override
  public GleamContext createContext(
      CreateContextDirective<BeamSettings, GleamIntegration> directive) {
    ServiceShape service = directive.service();
    BeamHttpBindings httpBindings = BeamHttpBindings.from(directive.model());
    Optional<ShapeId> resolved =
        BeamProtocolResolver.resolve(directive.model(), service, directive.settings());
    ShapeId resolvedProtocolTraitId = resolved.orElse(null);
    BeamProtocolCodegen protocolCodegen = null;
    if (resolved.isPresent()) {
      protocolCodegen =
          BeamProtocolCodegenFactory.create(
              directive.model(), resolved.get(), directive.integrations());
    }
    String ns = service.getId().getNamespace();
    BeamSettings settings = directive.settings();
    BeamGleamLayout layout = new BeamGleamLayout(settings, ns, service);
    return GleamContext.forServer(
        directive.model(),
        settings,
        directive.symbolProvider(),
        directive.fileManifest(),
        new WriterDelegator<>(
            directive.fileManifest(), directive.symbolProvider(), GleamWriter.factory()),
        directive.integrations(),
        service,
        httpBindings,
        protocolCodegen,
        resolvedProtocolTraitId,
        layout.serverModuleName(),
        layout.serverModuleFile());
  }

  @Override
  public void customizeBeforeShapeGeneration(
      CustomizeDirective<GleamContext, BeamSettings> directive) {
    GleamContext ctx = directive.context();
    ServiceShape service = ctx.service();
    BeamEdition edition = BeamEdition.fromSettings(directive.settings());
    BeamProtocolResolver.resolve(directive.model(), service, directive.settings())
        .ifPresent(
            protocol ->
                BeamProtocolResolver.assertClosureSupported(
                    directive.model(), service, protocol, edition));
  }

  @Override
  public void customizeBeforeIntegrations(
      CustomizeDirective<GleamContext, BeamSettings> directive) {}

  @Override
  public void customizeAfterIntegrations(
      CustomizeDirective<GleamContext, BeamSettings> directive) {}

  @Override
  public void generateService(GenerateServiceDirective<GleamContext, BeamSettings> directive) {
    GleamContext ctx = directive.context();
    ServiceShape service = directive.shape();
    String ns = service.getId().getNamespace();
    BeamGleamLayout layout = new BeamGleamLayout(ctx.settings(), ns, service);
    String typesModuleName = layout.typesModuleName();
    SymbolProvider sp = directive.symbolProvider();

    GleamModuleEmission.writeModule(
        ctx,
        layout.behaviourModuleFile(),
        GleamServerDsl.behaviourModule(
            layout, service, directive.model(), sp, typesModuleName));
    GleamModuleEmission.writeModule(
        ctx,
        layout.implModuleFile(),
        GleamServerDsl.implModule(layout, service, directive.model(), sp, typesModuleName));
    GleamModuleEmission.writeModule(
        ctx,
        layout.serverModuleFile(),
        GleamServerDsl.serverModule(layout, service, ctx.resolvedProtocolTraitId()));
    GleamProtocolCodecDsl.emitServerCodec(ctx, service);
    GleamModuleEmission.writeModule(
        ctx,
        layout.routerModuleFile(),
        GleamRouterDsl.routerModule(
            layout, service, directive.model(), sp, ctx.resolvedProtocolTraitId()));
  }

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
