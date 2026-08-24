package io.smithy.beam.erlang;

import io.beam.dsl.erlang.Module;
import io.smithy.beam.core.BeamCodegenKind;
import io.smithy.beam.core.BeamEdition;
import io.smithy.beam.core.BeamErlangLayout;
import io.smithy.beam.core.BeamHttpBindings;
import io.smithy.beam.core.BeamProtocolCodegen;
import io.smithy.beam.core.BeamProtocolCodegenFactory;
import io.smithy.beam.core.BeamProtocolResolver;
import io.smithy.beam.core.BeamResourceIndex;
import io.smithy.beam.core.BeamSettings;
import java.util.ArrayList;
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
 * Server-specific DirectedCodegen pass. Types are emitted by {@link ErlangTypeGeneration} before
 * this runs; this class must not write type files again.
 */
final class ErlangServerDirectedCodegen
    implements DirectedCodegen<ErlangContext, BeamSettings, ErlangIntegration> {

  @Override
  public SymbolProvider createSymbolProvider(
      CreateSymbolProviderDirective<BeamSettings> directive) {
    String ns = directive.service().getId().getNamespace();
    BeamSettings settings = directive.settings();
    BeamErlangLayout layout = new BeamErlangLayout(settings, ns, directive.service());
    String definitionFile = layout.serverModuleFile();
    return SymbolProvider.cache(
        new ErlangSymbolProvider(
            settings,
            directive.model(),
            directive.service(),
            definitionFile,
            BeamCodegenKind.SERVER));
  }

  @Override
  public ErlangContext createContext(
      CreateContextDirective<BeamSettings, ErlangIntegration> directive) {
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
    BeamErlangLayout layout = new BeamErlangLayout(settings, ns, service);
    String definitionFile = layout.serverModuleFile();
    String moduleName = layout.serverModuleName();
    return ErlangContext.forServer(
        directive.model(),
        directive.settings(),
        directive.symbolProvider(),
        directive.fileManifest(),
        new WriterDelegator<>(
            directive.fileManifest(), directive.symbolProvider(), ErlangWriter.factory()),
        directive.integrations(),
        service,
        httpBindings,
        protocolCodegen,
        resolvedProtocolTraitId,
        moduleName,
        definitionFile);
  }

  @Override
  public void customizeBeforeShapeGeneration(
      CustomizeDirective<ErlangContext, BeamSettings> directive) {
    ErlangContext ctx = directive.context();
    ServiceShape service = ctx.service();
    BeamEdition edition = BeamEdition.fromSettings(directive.settings());
    BeamProtocolResolver.resolve(directive.model(), service, directive.settings())
        .ifPresent(
            protocol ->
                BeamProtocolResolver.assertClosureSupported(
                    directive.model(), service, protocol, edition));

    List<OperationShape> operations = ErlangTopDown.containedOperationsSorted(ctx.model(), service);
    ErlangBehaviourEmitter.beginService(ctx, service, operations);
  }

  @Override
  public void customizeBeforeIntegrations(
      CustomizeDirective<ErlangContext, BeamSettings> directive) {
    // Reserved for server integrations.
  }

  @Override
  public void customizeAfterIntegrations(
      CustomizeDirective<ErlangContext, BeamSettings> directive) {
    // Reserved for server integrations.
  }

  @Override
  public void generateService(GenerateServiceDirective<ErlangContext, BeamSettings> directive) {
    ErlangContext ctx = directive.context();
    ServiceShape service = directive.shape();
    SymbolProvider sp = directive.symbolProvider();

    ErlangProtocolCodecDsl.emitServerCodec(ctx, service);

    ErlangRouterEmitter.emit(ctx, service);
    ErlangComplianceTestEmitter.emit(ctx, service, BeamCodegenKind.SERVER);
    BeamResourceIndex resourceIndex = BeamResourceIndex.of(ctx.model());
    for (ResourceShape resource : resourceIndex.containedResourcesSorted(service)) {
      ErlangResourceEmitter.emitServer(ctx, resource);
    }

    BeamErlangLayout layout =
        new BeamErlangLayout(ctx.settings(), service.getId().getNamespace(), service);
    List<OperationShape> operations = ErlangTopDown.containedOperationsSorted(ctx.model(), service);
    ErlangBehaviourEmitter.finishService(ctx, service, operations, sp);
    ErlangHandlerDiscoveryEmitter.emitDiscoveryHelpers(ctx, layout);

    ErlangServerModuleBuilder builder = ctx.serverModuleBuilderOrNull();
    if (builder != null) {
      List<String> exports = new ArrayList<>();
      exports.add("init_handlers/0");
      for (OperationShape op : operations) {
        Symbol sym = sp.toSymbol(op);
        exports.add("handle_" + sym.getName() + "/3");
      }
      Module module =
          ErlangServerDsl.serverModule(
              layout,
              service,
              layout.behaviourModuleName(),
              exports,
              builder.operationFunctions(),
              builder.discoveryFunctions());
      ErlangCodecEmission.writeModule(ctx, ctx.definitionFile(), module);
    }
  }

  @Override
  public void generateOperation(GenerateOperationDirective<ErlangContext, BeamSettings> directive) {
    ErlangContext ctx = directive.context();
    OperationShape op = directive.shape();
    SymbolProvider sp = directive.symbolProvider();
    Symbol opSym = sp.toSymbol(op);
    String handler = "handle_" + opSym.getName();

    ErlangHandlerDiscoveryEmitter.emitOperationDispatch(ctx, handler);
    ErlangBehaviourEmitter.emitOperationCallback(ctx, op, sp);
  }

  @Override
  public void generateResource(GenerateResourceDirective<ErlangContext, BeamSettings> directive) {
    // Emitted from generateService for all contained resources.
  }

  @Override
  public void generateEnumShape(GenerateEnumDirective<ErlangContext, BeamSettings> directive) {
    // Handled by ErlangTypeGeneration.
  }

  @Override
  public void generateIntEnumShape(
      GenerateIntEnumDirective<ErlangContext, BeamSettings> directive) {
    // Handled by ErlangTypeGeneration.
  }

  @Override
  public void generateUnion(GenerateUnionDirective<ErlangContext, BeamSettings> directive) {
    // Handled by ErlangTypeGeneration.
  }

  @Override
  public void generateStructure(GenerateStructureDirective<ErlangContext, BeamSettings> directive) {
    // Handled by ErlangTypeGeneration.
  }

  @Override
  public void generateError(GenerateErrorDirective<ErlangContext, BeamSettings> directive) {
    // Handled by ErlangTypeGeneration.
  }
}
