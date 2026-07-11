package io.smithy.beam.erlang;

import io.beam.ir.erlang.AtomExpr;
import io.beam.ir.erlang.BlockExpr;
import io.beam.ir.erlang.Edoc;
import io.beam.ir.erlang.Expression;
import io.beam.ir.erlang.Function;
import io.beam.ir.erlang.FunctionClause;
import io.beam.ir.erlang.Module;
import io.beam.ir.erlang.Spec;
import io.beam.ir.erlang.TupleExpr;
import io.beam.ir.erlang.VariablePattern;
import io.smithy.beam.core.BeamClientPaginationSupport;
import io.smithy.beam.core.BeamClientRetrySupport;
import io.smithy.beam.core.BeamCodegenKind;
import io.smithy.beam.core.BeamDocumentation;
import io.smithy.beam.core.BeamEdition;
import io.smithy.beam.core.BeamErlangLayout;
import io.smithy.beam.core.BeamHttpBindings;
import io.smithy.beam.core.BeamProtocolCodegen;
import io.smithy.beam.core.BeamProtocolCodegenFactory;
import io.smithy.beam.core.BeamProtocolResolver;
import io.smithy.beam.core.BeamProtocolSupport;
import io.smithy.beam.core.BeamResourceIndex;
import io.smithy.beam.core.BeamSettings;
import java.util.ArrayList;
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
 * Client-specific DirectedCodegen. Types are emitted by {@link ErlangTypeGeneration} before this
 * runs; this class must not write type files again.
 */
final class ErlangClientDirectedCodegen
    implements DirectedCodegen<ErlangContext, BeamSettings, ErlangIntegration> {

  @Override
  public SymbolProvider createSymbolProvider(
      CreateSymbolProviderDirective<BeamSettings> directive) {
    String ns = directive.service().getId().getNamespace();
    BeamSettings settings = directive.settings();
    BeamErlangLayout layout = new BeamErlangLayout(settings, ns, directive.service());
    String definitionFile = layout.clientModuleFile();
    return SymbolProvider.cache(
        new ErlangSymbolProvider(
            settings,
            directive.model(),
            directive.service(),
            definitionFile,
            BeamCodegenKind.CLIENT));
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
    String definitionFile = layout.clientModuleFile();
    String moduleName = layout.clientModuleName();
    return new ErlangContext(
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
        definitionFile,
        new ErlangClientModuleBuilder());
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
  }

  @Override
  public void customizeBeforeIntegrations(
      CustomizeDirective<ErlangContext, BeamSettings> directive) {
    // Reserved for client integrations.
  }

  @Override
  public void customizeAfterIntegrations(
      CustomizeDirective<ErlangContext, BeamSettings> directive) {
    // Reserved for client integrations.
  }

  @Override
  public void generateService(GenerateServiceDirective<ErlangContext, BeamSettings> directive) {
    ErlangContext ctx = directive.context();
    ServiceShape service = directive.shape();
    SymbolProvider sp = directive.symbolProvider();
    String ns = service.getId().getNamespace();
    BeamErlangLayout layout = new BeamErlangLayout(ctx.settings(), ns, service);

    ErlangProtocolCodecIr.emitClientCodec(ctx, service);

    ErlangS3EndpointEmitter.emit(ctx, service);
    ErlangWaiterEmitter.emit(ctx, service);
    ErlangComplianceTestEmitter.emit(ctx, service);
    ErlangEventStreamEmitter.emit(ctx, service);
    BeamResourceIndex resourceIndex = BeamResourceIndex.of(ctx.model());
    for (ResourceShape resource : resourceIndex.containedResourcesSorted(service)) {
      ErlangResourceEmitter.emitClient(ctx, resource);
    }

    ErlangClientModuleBuilder builder = ctx.clientModuleBuilderOrNull();
    if (builder != null) {
      List<String> exports = new ArrayList<>();
      List<OperationShape> operations =
          ErlangTopDown.containedOperationsSorted(ctx.model(), service);
      for (OperationShape op : operations) {
        exports.add(sp.toSymbol(op).getName() + "/2");
      }
      if (ErlangRetryIr.serviceHasRetryableErrors(ctx.model(), service)) {
        builder.addOperationFunctions(
            ErlangRetryIr.clientPredicateFunctions(ctx.model(), service, sp));
      }
      Module module =
          ErlangClientIr.clientModule(layout, service, exports, builder.operationFunctions());
      ErlangCodecEmission.writeModule(ctx, ctx.definitionFile(), module);
      if (ctx.protocolCodegen() != null) {
        for (OperationShape op : operations) {
          ctx.writerDelegator()
              .useFileWriter(
                  ctx.definitionFile(),
                  writer -> {
                    ctx.protocolCodegen().emitOperationBindings(ctx, service, op);
                    for (ErlangIntegration integration : ctx.integrations()) {
                      integration.customizeProtocolSerialize(ctx, op, writer);
                      integration.customizeProtocolDeserialize(ctx, op, writer);
                    }
                  });
        }
      }
    }
  }

  @Override
  public void generateOperation(GenerateOperationDirective<ErlangContext, BeamSettings> directive) {
    ErlangContext ctx = directive.context();
    ErlangClientModuleBuilder builder = ctx.clientModuleBuilderOrNull();
    if (builder == null) {
      return;
    }

    OperationShape op = directive.shape();
    SymbolProvider sp = directive.symbolProvider();
    Symbol opSym = sp.toSymbol(op);

    StructureShape input = ctx.model().expectShape(op.getInputShape(), StructureShape.class);
    StructureShape output = ctx.model().expectShape(op.getOutputShape(), StructureShape.class);
    Symbol inSym = sp.toSymbol(input);
    Symbol outSym = sp.toSymbol(output);

    BeamErlangLayout layout =
        new BeamErlangLayout(ctx.settings(), ctx.service().getId().getNamespace(), ctx.service());
    boolean hasProtocol =
        BeamProtocolSupport.hasWireCodegen(
            ctx.resolvedProtocolTraitId(), ctx.protocolCodegen(), ctx.integrations());
    boolean wrapWithRetry = BeamClientRetrySupport.operationHasRetryableErrors(ctx.model(), op);
    boolean paginated = BeamClientPaginationSupport.isPaginated(ctx.model(), ctx.service(), op);
    PaginationInfo paginationInfo =
        paginated
            ? BeamClientPaginationSupport.requirePaginationInfo(ctx.model(), ctx.service(), op)
            : null;

    String successReturnType = successReturnType(paginated, paginationInfo, ctx, sp, outSym);

    if (paginated) {
      builder.addOperationFunctions(
          ErlangClientPaginationIr.paginatedOperationFunctions(
              ctx,
              ctx.service(),
              op,
              layout,
              wrapWithRetry,
              successReturnType,
              operationDoc(op, ctx)));
      return;
    }

    builder.addOperationFunction(
        singlePageOperationFunction(
            ctx,
            op,
            layout,
            opSym,
            inSym,
            successReturnType,
            hasProtocol,
            wrapWithRetry,
            operationDoc(op, ctx)));
  }

  private static String successReturnType(
      boolean paginated,
      PaginationInfo paginationInfo,
      ErlangContext ctx,
      SymbolProvider sp,
      Symbol outSym) {
    if (paginated && BeamClientPaginationSupport.hasItemsMember(paginationInfo)) {
      return "["
          + BeamClientPaginationSupport.itemsElementSymbol(ctx.model(), sp, paginationInfo)
              .orElseThrow()
              .getName()
          + "]";
    }
    if (paginated) {
      return "[" + outSym.getName() + "]";
    }
    return outSym.getName();
  }

  private static Edoc operationDoc(OperationShape op, ErlangContext ctx) {
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
    return Edoc.of(text.toString().strip());
  }

  private static Function singlePageOperationFunction(
      ErlangContext ctx,
      OperationShape op,
      BeamErlangLayout layout,
      Symbol opSym,
      Symbol inSym,
      String successReturnType,
      boolean hasProtocol,
      boolean wrapWithRetry,
      Edoc doc) {
    String specOutput = "{'ok', " + successReturnType + "} | {'error', term()}";
    Spec spec =
        Spec.of(opSym.getName() + "(client_config(), " + inSym.getName() + ") -> " + specOutput);
    if (!hasProtocol) {
      return Function.of(
          opSym.getName(),
          List.of(
              FunctionClause.of(
                  List.of(VariablePattern.of("_Config"), VariablePattern.of("_Input")),
                  TupleExpr.of(List.of(AtomExpr.of("error"), AtomExpr.of("not_implemented"))))),
          spec,
          doc);
    }

    List<Expression> body =
        ErlangClientDispatchIr.operationBodyExprs(
            ctx,
            op,
            layout,
            wrapWithRetry,
            false,
            ErlangClientDispatchOperationIr.DispatchBodyMode.SINGLE_PAGE);
    Expression clauseBody = body.size() == 1 ? body.get(0) : BlockExpr.commaSeparated(body, false);
    return Function.of(
        opSym.getName(),
        List.of(
            FunctionClause.of(
                List.of(VariablePattern.of("Config"), VariablePattern.of("Input")), clauseBody)),
        spec,
        doc);
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
