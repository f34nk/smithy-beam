package io.smithy.beam.elixir;

import io.smithy.beam.core.BeamClientPaginationSupport;
import io.smithy.beam.core.BeamClientRetrySupport;
import io.smithy.beam.core.BeamCodegenKind;
import io.smithy.beam.core.BeamDocumentation;
import io.smithy.beam.core.BeamEdition;
import io.smithy.beam.core.BeamElixirLayout;
import io.smithy.beam.core.BeamHttpBindings;
import io.smithy.beam.core.BeamProtocolCodegen;
import io.smithy.beam.core.BeamProtocolCodegenFactory;
import io.smithy.beam.core.BeamProtocolResolver;
import io.smithy.beam.core.BeamProtocolSupport;
import io.smithy.beam.core.BeamResourceIndex;
import io.smithy.beam.core.BeamSettings;
import io.smithy.beam.ir.elixir.ExAtom;
import io.smithy.beam.ir.elixir.ExClause;
import io.smithy.beam.ir.elixir.ExDoc;
import io.smithy.beam.ir.elixir.ExExpr;
import io.smithy.beam.ir.elixir.ExExprBlock;
import io.smithy.beam.ir.elixir.ExFunction;
import io.smithy.beam.ir.elixir.ExModule;
import io.smithy.beam.ir.elixir.ExSpec;
import io.smithy.beam.ir.elixir.ExTuple;
import io.smithy.beam.ir.elixir.ExVarPattern;
import java.util.List;
import java.util.Map;
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
import software.amazon.smithy.model.knowledge.HttpBinding;
import software.amazon.smithy.model.knowledge.PaginationInfo;
import software.amazon.smithy.model.shapes.OperationShape;
import software.amazon.smithy.model.shapes.ResourceShape;
import software.amazon.smithy.model.shapes.ServiceShape;
import software.amazon.smithy.model.shapes.ShapeId;
import software.amazon.smithy.model.shapes.StructureShape;

/**
 * Client-specific DirectedCodegen. Types are emitted by {@link ElixirTypeGeneration} before this
 * runs; this class must not write type files again.
 */
final class ElixirClientDirectedCodegen
    implements DirectedCodegen<ElixirContext, BeamSettings, ElixirIntegration> {

  @Override
  public SymbolProvider createSymbolProvider(
      CreateSymbolProviderDirective<BeamSettings> directive) {
    String ns = directive.service().getId().getNamespace();
    BeamSettings settings = directive.settings();
    BeamElixirLayout layout = new BeamElixirLayout(settings, ns, directive.service());
    String definitionFile = layout.clientModuleFile();
    String clientModuleName = ElixirSymbolProvider.toModuleName(layout.clientModuleName());
    return SymbolProvider.cache(
        new ElixirSymbolProvider(
            settings,
            directive.model(),
            directive.service(),
            definitionFile,
            clientModuleName,
            BeamCodegenKind.CLIENT));
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
    BeamElixirLayout layout = new BeamElixirLayout(settings, ns, service);
    String definitionFile = layout.clientModuleFile();
    String clientModuleName = ElixirSymbolProvider.toModuleName(layout.clientModuleName());
    return new ElixirContext(
        directive.model(),
        directive.settings(),
        directive.symbolProvider(),
        directive.fileManifest(),
        new WriterDelegator<>(
            directive.fileManifest(),
            directive.symbolProvider(),
            ElixirWriter.factory(clientModuleName)),
        directive.integrations(),
        service,
        httpBindings,
        protocolCodegen,
        resolvedProtocolTraitId,
        clientModuleName,
        definitionFile,
        new java.util.ArrayList<>(),
        new java.util.ArrayList<>(),
        new java.util.ArrayList<>(),
        new ElixirClientModuleBuilder(),
        null,
        null);
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
  }

  @Override
  public void customizeBeforeIntegrations(
      CustomizeDirective<ElixirContext, BeamSettings> directive) {
    // Reserved for client integrations.
  }

  @Override
  public void customizeAfterIntegrations(
      CustomizeDirective<ElixirContext, BeamSettings> directive) {
    // Reserved for client integrations.
  }

  @Override
  public void generateService(GenerateServiceDirective<ElixirContext, BeamSettings> directive) {
    ElixirContext ctx = directive.context();
    ServiceShape service = directive.shape();
    SymbolProvider sp = directive.symbolProvider();
    String ns = service.getId().getNamespace();
    BeamElixirLayout layout = new BeamElixirLayout(ctx.settings(), ns, service);

    ElixirProtocolCodecIr.emitClientCodec(ctx, service);

    ElixirS3EndpointEmitter.emit(ctx, service);
    ElixirCredentialProviderEmitter.emit(ctx, service);
    ElixirRetryEmitter.emit(ctx, service);
    ElixirWaiterEmitter.emit(ctx, service);
    ElixirComplianceTestEmitter.emit(ctx, service);
    ElixirEventStreamEmitter.emit(ctx, service);
    BeamResourceIndex resourceIndex = BeamResourceIndex.of(ctx.model());
    for (ResourceShape resource : resourceIndex.containedResourcesSorted(service)) {
      ElixirResourceEmitter.emitClient(ctx, resource);
    }

    ElixirClientModuleBuilder builder = ctx.clientModuleBuilderOrNull();
    if (builder != null) {
      String typesModuleName = ElixirSymbolProvider.toModuleName(layout.typesModuleName());
      ExModule module =
          ElixirClientIr.clientModule(
              layout, service, typesModuleName, builder.operationFunctions());
      ctx.writerDelegator()
          .useFileWriter(ctx.definitionFile(), writer -> writer.write("$L", module.asString()));
      if (ctx.protocolCodegen() != null) {
        List<OperationShape> operations =
            ElixirTopDown.containedOperationsSorted(ctx.model(), service);
        for (OperationShape op : operations) {
          ctx.writerDelegator()
              .useFileWriter(
                  ctx.definitionFile(),
                  writer -> {
                    ctx.protocolCodegen().emitOperationBindings(ctx, service, op);
                    for (ElixirIntegration integration : ctx.integrations()) {
                      integration.customizeProtocolSerialize(ctx, op, writer);
                      integration.customizeProtocolDeserialize(ctx, op, writer);
                    }
                  });
        }
      }
    }
  }

  @Override
  public void generateOperation(GenerateOperationDirective<ElixirContext, BeamSettings> directive) {
    ElixirContext ctx = directive.context();
    ElixirClientModuleBuilder builder = ctx.clientModuleBuilderOrNull();
    if (builder == null) {
      return;
    }

    OperationShape op = directive.shape();
    SymbolProvider sp = directive.symbolProvider();
    Symbol opSym = sp.toSymbol(op);

    String ns = ctx.service().getId().getNamespace();
    BeamElixirLayout layout = new BeamElixirLayout(ctx.settings(), ns, ctx.service());
    String typesModuleName = ElixirSymbolProvider.toModuleName(layout.typesModuleName());

    StructureShape input = ctx.model().expectShape(op.getInputShape(), StructureShape.class);
    StructureShape output = ctx.model().expectShape(op.getOutputShape(), StructureShape.class);
    Symbol inSym = sp.toSymbol(input);
    Symbol outSym = sp.toSymbol(output);
    String inType = ElixirTopDown.structureSpecType(typesModuleName, inSym);

    boolean hasProtocol =
        BeamProtocolSupport.hasWireCodegen(
            ctx.resolvedProtocolTraitId(), ctx.protocolCodegen(), ctx.integrations());
    boolean wrapWithRetry = BeamClientRetrySupport.operationHasRetryableErrors(ctx.model(), op);
    String retryModule = ElixirSymbolProvider.toModuleName(layout.retryModuleName());
    boolean paginated = BeamClientPaginationSupport.isPaginated(ctx.model(), ctx.service(), op);
    PaginationInfo paginationInfo =
        paginated
            ? BeamClientPaginationSupport.requirePaginationInfo(ctx.model(), ctx.service(), op)
            : null;

    String successReturnType =
        successReturnType(paginated, paginationInfo, ctx, sp, typesModuleName, outSym);
    ExDoc doc = operationDoc(op, ctx);

    if (paginated) {
      builder.addOperationFunctions(
          ElixirClientPaginationIr.paginatedOperationFunctions(
              ctx, ctx.service(), op, layout, wrapWithRetry, retryModule, successReturnType, doc));
      return;
    }

    builder.addOperationFunction(
        singlePageOperationFunction(
            ctx,
            op,
            layout,
            opSym,
            inType,
            successReturnType,
            hasProtocol,
            wrapWithRetry,
            retryModule,
            doc));
  }

  private static String successReturnType(
      boolean paginated,
      PaginationInfo paginationInfo,
      ElixirContext ctx,
      SymbolProvider sp,
      String typesModuleName,
      Symbol outSym) {
    if (paginated && BeamClientPaginationSupport.hasItemsMember(paginationInfo)) {
      String itemType =
          ElixirTopDown.structureSpecType(
              typesModuleName,
              BeamClientPaginationSupport.itemsElementSymbol(ctx.model(), sp, paginationInfo)
                  .orElseThrow());
      return "[" + itemType + "]";
    }
    if (paginated) {
      return "[" + ElixirTopDown.structureSpecType(typesModuleName, outSym) + "]";
    }
    return ElixirTopDown.structureSpecType(typesModuleName, outSym);
  }

  private static ExDoc operationDoc(OperationShape op, ElixirContext ctx) {
    StringBuilder text = new StringBuilder();
    BeamDocumentation.forShape(op).ifPresent(doc -> text.append(doc).append('\n'));
    if (ctx.protocolCodegen() != null) {
      Map<String, HttpBinding> bindings = ctx.httpBindings().requestBindings(op);
      if (!bindings.isEmpty()) {
        text.append("HTTP request bindings for ").append(op.getId()).append(':').append('\n');
        for (Map.Entry<String, HttpBinding> entry : bindings.entrySet()) {
          HttpBinding binding = entry.getValue();
          text.append("  ")
              .append(entry.getKey())
              .append(" @ ")
              .append(binding.getLocation())
              .append('\n');
        }
      }
    }
    if (text.isEmpty()) {
      return null;
    }
    return ExDoc.doc(text.toString().strip());
  }

  private static ExFunction singlePageOperationFunction(
      ElixirContext ctx,
      OperationShape op,
      BeamElixirLayout layout,
      Symbol opSym,
      String inType,
      String successReturnType,
      boolean hasProtocol,
      boolean wrapWithRetry,
      String retryModule,
      ExDoc doc) {
    String specOutput = "{:ok, " + successReturnType + "} | {:error, term()}";
    if (!hasProtocol) {
      return ExFunction.functionWithDocAndSpec(
          "def",
          opSym.getName(),
          doc,
          ExSpec.functionSpec(opSym.getName(), "map(), " + inType, specOutput),
          List.of(
              ExClause.inlineClause(
                  List.of(ExVarPattern.var("_config"), ExVarPattern.var("_input")),
                  ExTuple.tuple(ExAtom.atom("error"), ExAtom.atom("not_implemented")))));
    }

    List<ExExpr> body =
        ElixirClientDispatchIr.operationBodyExprs(
            ctx,
            op,
            layout,
            wrapWithRetry,
            retryModule,
            false,
            ElixirClientDispatchOperationIr.DispatchBodyMode.SINGLE_PAGE);
    return ExFunction.functionWithDocAndSpec(
        "def",
        opSym.getName(),
        doc,
        ExSpec.functionSpec(opSym.getName(), "map(), " + inType, specOutput),
        List.of(
            ExClause.blockClause(
                List.of(ExVarPattern.var("config"), ExVarPattern.var("input")),
                ExExprBlock.block(body.toArray(ExExpr[]::new)))));
  }

  @Override
  public void generateResource(GenerateResourceDirective<ElixirContext, BeamSettings> directive) {
    // Emitted from generateService for all contained resources.
  }

  @Override
  public void generateEnumShape(GenerateEnumDirective<ElixirContext, BeamSettings> directive) {
    // Handled by ElixirTypeGeneration.
  }

  @Override
  public void generateIntEnumShape(
      GenerateIntEnumDirective<ElixirContext, BeamSettings> directive) {
    // Handled by ElixirTypeGeneration.
  }

  @Override
  public void generateUnion(GenerateUnionDirective<ElixirContext, BeamSettings> directive) {
    // Handled by ElixirTypeGeneration.
  }

  @Override
  public void generateStructure(GenerateStructureDirective<ElixirContext, BeamSettings> directive) {
    // Handled by ElixirTypeGeneration.
  }

  @Override
  public void generateError(GenerateErrorDirective<ElixirContext, BeamSettings> directive) {
    // Handled by ElixirTypeGeneration.
  }
}
