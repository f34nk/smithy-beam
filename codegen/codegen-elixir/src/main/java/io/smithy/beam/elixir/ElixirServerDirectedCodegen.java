package io.smithy.beam.elixir;

import io.smithy.beam.core.BeamCodegenKind;
import io.smithy.beam.core.BeamEdition;
import io.smithy.beam.core.BeamElixirLayout;
import io.smithy.beam.core.BeamHttpBindings;
import io.smithy.beam.core.BeamProtocolCodegen;
import io.smithy.beam.core.BeamProtocolCodegenFactory;
import io.smithy.beam.core.BeamProtocolResolver;
import io.smithy.beam.core.BeamResourceIndex;
import io.smithy.beam.core.BeamSettings;
import io.smithy.beam.ir.elixir.ExModule;
import java.util.List;
import java.util.Optional;
import software.amazon.smithy.codegen.core.Symbol;
import software.amazon.smithy.codegen.core.SymbolProvider;
import software.amazon.smithy.codegen.core.WriterDelegator;
import software.amazon.smithy.codegen.core.directed.CreateContextDirective;
import software.amazon.smithy.codegen.core.directed.CreateSymbolProviderDirective;
import software.amazon.smithy.codegen.core.directed.CustomizeDirective;
import software.amazon.smithy.codegen.core.directed.DirectedCodegen;
import software.amazon.smithy.codegen.core.directed.GenerateEnumDirective;
import software.amazon.smithy.codegen.core.directed.GenerateErrorDirective;
import software.amazon.smithy.codegen.core.directed.GenerateIntEnumDirective;
import software.amazon.smithy.codegen.core.directed.GenerateOperationDirective;
import software.amazon.smithy.codegen.core.directed.GenerateResourceDirective;
import software.amazon.smithy.codegen.core.directed.GenerateServiceDirective;
import software.amazon.smithy.codegen.core.directed.GenerateStructureDirective;
import software.amazon.smithy.codegen.core.directed.GenerateUnionDirective;
import software.amazon.smithy.model.shapes.OperationShape;
import software.amazon.smithy.model.shapes.ResourceShape;
import software.amazon.smithy.model.shapes.ServiceShape;
import software.amazon.smithy.model.shapes.ShapeId;

/**
 * Server-specific DirectedCodegen pass. Types are emitted by {@link ElixirTypeGeneration} before
 * this runs; this class must not write type files again.
 */
final class ElixirServerDirectedCodegen
    implements DirectedCodegen<ElixirContext, BeamSettings, ElixirIntegration> {

  @Override
  public SymbolProvider createSymbolProvider(
      CreateSymbolProviderDirective<BeamSettings> directive) {
    String ns = directive.service().getId().getNamespace();
    BeamSettings settings = directive.settings();
    BeamElixirLayout layout = new BeamElixirLayout(settings, ns, directive.service());
    String definitionFile = layout.serverModuleFile();
    String serverModuleName = ElixirSymbolProvider.toModuleName(layout.serverModuleName());
    return SymbolProvider.cache(
        new ElixirSymbolProvider(
            settings,
            directive.model(),
            directive.service(),
            definitionFile,
            serverModuleName,
            BeamCodegenKind.SERVER));
  }

  @Override
  public ElixirContext createContext(
      CreateContextDirective<BeamSettings, ElixirIntegration> directive) {
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
    BeamElixirLayout layout = new BeamElixirLayout(settings, ns, directive.service());
    String definitionFile = layout.serverModuleFile();
    String serverModuleName = ElixirSymbolProvider.toModuleName(layout.serverModuleName());
    return new ElixirContext(
        directive.model(),
        directive.settings(),
        directive.symbolProvider(),
        directive.fileManifest(),
        new WriterDelegator<>(
            directive.fileManifest(),
            directive.symbolProvider(),
            ElixirWriter.factory(serverModuleName)),
        directive.integrations(),
        service,
        httpBindings,
        protocolCodegen,
        resolvedProtocolTraitId,
        serverModuleName,
        definitionFile,
        new java.util.ArrayList<>(),
        new java.util.ArrayList<>(),
        new java.util.ArrayList<>(),
        null,
        new ElixirBehaviourModuleBuilder(),
        new ElixirServerModuleBuilder());
  }

  @Override
  public void customizeBeforeShapeGeneration(
      CustomizeDirective<ElixirContext, BeamSettings> directive) {
    ElixirContext ctx = directive.context();
    ServiceShape service = ctx.service();
    BeamEdition edition = BeamEdition.fromSettings(directive.settings());
    BeamProtocolResolver.resolve(directive.model(), service, directive.settings())
        .ifPresent(
            protocol ->
                BeamProtocolResolver.assertClosureSupported(
                    directive.model(), service, protocol, edition));

    List<OperationShape> operations = ElixirTopDown.containedOperationsSorted(ctx.model(), service);
    ElixirBehaviourEmitter.beginService(ctx, service, operations);
  }

  @Override
  public void customizeBeforeIntegrations(
      CustomizeDirective<ElixirContext, BeamSettings> directive) {
    // Reserved for server integrations.
  }

  @Override
  public void customizeAfterIntegrations(
      CustomizeDirective<ElixirContext, BeamSettings> directive) {
    // Server module is written as a single ExModule in generateService.
  }

  @Override
  public void generateService(GenerateServiceDirective<ElixirContext, BeamSettings> directive) {
    ElixirContext ctx = directive.context();
    ServiceShape service = directive.shape();

    ElixirProtocolCodecIr.emitServerCodec(ctx, service);

    ElixirRouterEmitter.emit(ctx, service);
    ElixirComplianceTestEmitter.emit(ctx, service);
    BeamResourceIndex resourceIndex = BeamResourceIndex.of(ctx.model());
    for (ResourceShape resource : resourceIndex.containedResourcesSorted(service)) {
      ElixirResourceEmitter.emitServer(ctx, resource);
    }

    BeamElixirLayout layout =
        new BeamElixirLayout(ctx.settings(), service.getId().getNamespace(), service);
    List<OperationShape> operations = ElixirTopDown.containedOperationsSorted(ctx.model(), service);
    ElixirBehaviourEmitter.finishService(ctx, operations, directive.symbolProvider());
    ElixirHandlerDiscoveryEmitter.emitDiscoveryHelpers(ctx, layout);

    ElixirServerModuleBuilder builder = ctx.serverModuleBuilderOrNull();
    if (builder != null) {
      String behaviourMod = ElixirSymbolProvider.toModuleName(layout.behaviourModuleName());
      String typesMod = ElixirSymbolProvider.toModuleName(layout.typesModuleName());
      ExModule module =
          ElixirServerIr.serverModule(
              layout,
              service,
              behaviourMod,
              typesMod,
              builder.operationFunctions(),
              builder.discoveryFunctions());
      ElixirCodecEmission.writeModule(ctx, ctx.definitionFile(), module);
    }
  }

  @Override
  public void generateOperation(GenerateOperationDirective<ElixirContext, BeamSettings> directive) {
    ElixirContext ctx = directive.context();
    OperationShape op = directive.shape();
    SymbolProvider sp = directive.symbolProvider();
    Symbol opSym = sp.toSymbol(op);
    String handler = "handle_" + opSym.getName();

    ElixirHandlerDiscoveryEmitter.emitOperationDispatch(ctx, handler);

    ElixirBehaviourEmitter.emitOperationCallback(ctx, op, sp);
  }

  @Override
  public void generateResource(GenerateResourceDirective<ElixirContext, BeamSettings> directive) {
    // Emitted from generateService for all contained resources.
  }

  @Override
  public void generateEnumShape(GenerateEnumDirective<ElixirContext, BeamSettings> directive) {
    // Types-only: handled by ElixirTypeGeneration.
  }

  @Override
  public void generateIntEnumShape(
      GenerateIntEnumDirective<ElixirContext, BeamSettings> directive) {
    // Types-only: handled by ElixirTypeGeneration.
  }

  @Override
  public void generateUnion(GenerateUnionDirective<ElixirContext, BeamSettings> directive) {
    // Types-only: handled by ElixirTypeGeneration.
  }

  @Override
  public void generateStructure(GenerateStructureDirective<ElixirContext, BeamSettings> directive) {
    // Types-only: handled by ElixirTypeGeneration.
  }

  @Override
  public void generateError(GenerateErrorDirective<ElixirContext, BeamSettings> directive) {
    // Types-only: handled by ElixirTypeGeneration.
  }
}
