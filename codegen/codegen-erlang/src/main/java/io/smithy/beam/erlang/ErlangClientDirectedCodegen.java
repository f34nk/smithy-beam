package io.smithy.beam.erlang;

import io.smithy.beam.core.BeamAwsServiceMetadata;
import io.smithy.beam.core.BeamClientPaginationSupport;
import io.smithy.beam.core.BeamClientRetrySupport;
import io.smithy.beam.core.BeamCodegenKind;
import io.smithy.beam.core.BeamDocumentation;
import io.smithy.beam.core.BeamErlangLayout;
import io.smithy.beam.core.BeamEndpointRuleSetEmitter;
import io.smithy.beam.core.BeamHttpBindings;
import io.smithy.beam.core.BeamProtocolCodegen;
import io.smithy.beam.core.BeamProtocolCodegenFactory;
import io.smithy.beam.core.BeamAwsJson10ProtocolCodegen;
import io.smithy.beam.core.BeamAwsJson11ProtocolCodegen;
import io.smithy.beam.core.BeamAwsQueryProtocolCodegen;
import io.smithy.beam.core.BeamEc2QueryProtocolCodegen;
import io.smithy.beam.core.BeamRestJson1ProtocolCodegen;
import io.smithy.beam.core.BeamRestXmlProtocolCodegen;
import io.smithy.beam.core.BeamEdition;
import io.smithy.beam.core.BeamProtocolResolver;
import io.smithy.beam.core.BeamProtocolSupport;
import io.smithy.beam.core.BeamResourceIndex;
import io.smithy.beam.core.BeamSettings;
import io.smithy.beam.core.BeamSigV4Index;
import io.smithy.beam.core.BeamSigV4Metadata;
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
import software.amazon.smithy.model.shapes.MemberShape;
import software.amazon.smithy.model.shapes.OperationShape;
import software.amazon.smithy.model.shapes.ResourceShape;
import software.amazon.smithy.model.shapes.ServiceShape;
import software.amazon.smithy.model.shapes.ShapeId;
import software.amazon.smithy.model.shapes.StructureShape;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Client-specific DirectedCodegen. Types are emitted by {@link ErlangTypeGeneration}
 * before this runs; this class must not write type files again.
 */
final class ErlangClientDirectedCodegen
        implements DirectedCodegen<ErlangContext, BeamSettings, ErlangIntegration> {

    @Override
    public SymbolProvider createSymbolProvider(
            CreateSymbolProviderDirective<BeamSettings> directive) {
        String ns = directive.service().getId().getNamespace();
        String serviceName = directive.service().getId().getName();
        BeamSettings settings = directive.settings();
        BeamErlangLayout layout = new BeamErlangLayout(settings, ns, serviceName);
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
        String serviceName = service.getId().getName();
        BeamSettings settings = directive.settings();
        BeamErlangLayout layout = new BeamErlangLayout(settings, ns, serviceName);
        String definitionFile = layout.clientModuleFile();
        String moduleName = layout.clientModuleName();
        return new ErlangContext(
                directive.model(),
                directive.settings(),
                directive.symbolProvider(),
                directive.fileManifest(),
                new WriterDelegator<>(
                        directive.fileManifest(),
                        directive.symbolProvider(),
                        ErlangWriter.factory()),
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

        String ns = service.getId().getNamespace();
        BeamErlangLayout layout = new BeamErlangLayout(
                ctx.settings(), ns, service.getId().getName());

        ctx.writerDelegator().useFileWriter(
                layout.runtimeTypesHeaderFile(),
                writer -> {
                    writer.write("%% Generated runtime types for $L.", ctx.service().getId());
                    Optional<String> ruleSet =
                            BeamEndpointRuleSetEmitter.serializeRuleSetErlangMap(
                                    directive.model(), service);
                    ErlangRuntimeTypesEmitter.writeBody(writer, ruleSet);
                });

        List<OperationShape> operations = ErlangTopDown.containedOperationsSorted(ctx.model(), service);
        List<String> exports = new ArrayList<>();
        for (OperationShape op : operations) {
            Symbol sym = directive.symbolProvider().toSymbol(op);
            exports.add(sym.getName() + "/2");
        }
        String exportList = String.join(", ", exports);

        ctx.writerDelegator().useFileWriter(layout.clientModuleFile(), writer -> {
            writer.pushGeneratedDocumentationSection();
            writer.write("%% Generated Erlang client for $L.", service.getId());
            writer.write("%% Operation stubs use arity 2: (Config, Input).");
            writer.popState();

            writer.pushModuleHeaderSection();
            writer.write("-module($L).", layout.clientModuleName());
            writer.popState();

            writer.pushDependenciesSection();
            ((ErlangImports) writer.getImportContainer()).addIncludeRelative(layout.typesHeaderFile());
            writer.write(ErlangImports.relativeIncludeLine(layout.typesHeaderFile()));
            writer.popState();

            writer.pushModuleHeaderSection();
            if (exportList.isEmpty()) {
                writer.write("-export([]).");
            } else {
                ErlangFormat.writeExport(writer, exports);
            }
            writer.write("");
            writer.popState();
        });
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
    public void generateService(
            GenerateServiceDirective<ErlangContext, BeamSettings> directive) {
        ErlangContext ctx = directive.context();
        ServiceShape service = directive.shape();

        if (ctx.protocolCodegen() != null
                && BeamRestJson1ProtocolCodegen.REST_JSON_1.equals(
                        ctx.protocolCodegen().protocolTraitId())) {
            ErlangRestJson1Emitter.emitCodecModule(ctx, directive.shape());
        } else if (ctx.protocolCodegen() != null
                && BeamAwsJson10ProtocolCodegen.AWS_JSON_1_0.equals(
                        ctx.protocolCodegen().protocolTraitId())) {
            ErlangAwsJson10Emitter.emitCodecModule(ctx, directive.shape());
        } else if (ctx.protocolCodegen() != null
                && BeamAwsJson11ProtocolCodegen.AWS_JSON_1_1.equals(
                        ctx.protocolCodegen().protocolTraitId())) {
            ErlangAwsJson11Emitter.emitCodecModule(ctx, directive.shape());
        } else if (ctx.protocolCodegen() != null
                && BeamAwsQueryProtocolCodegen.AWS_QUERY.equals(
                        ctx.protocolCodegen().protocolTraitId())) {
            ErlangAwsQueryEmitter.emitCodecModule(ctx, directive.shape());
        } else if (ctx.protocolCodegen() != null
                && BeamEc2QueryProtocolCodegen.EC2_QUERY.equals(
                        ctx.protocolCodegen().protocolTraitId())) {
            ErlangEc2QueryEmitter.emitCodecModule(ctx, directive.shape());
        } else if (ctx.protocolCodegen() != null
                && BeamRestXmlProtocolCodegen.REST_XML.equals(
                        ctx.protocolCodegen().protocolTraitId())) {
            ErlangRestXmlEmitter.emitCodecModule(ctx, directive.shape());
        }

        ErlangRuntimeHelpersEmitter.emitIfNeeded(ctx, service);
        ErlangAwsEndpointRulesEmitter.emitIfNeeded(ctx, service);
        ErlangS3EndpointEmitter.emit(ctx, service);
        ErlangEndpointRulesEmitter.emit(ctx, service);
        ErlangHttpDispatchEmitter.emit(ctx, service);
        ErlangSigV4Emitter.emit(ctx, service);
        ErlangPresignerEmitter.emit(ctx, service);
        ErlangCredentialProviderEmitter.emit(ctx, service);
        ErlangRetryEmitter.emit(ctx, service);
        ErlangWaiterEmitter.emit(ctx, service);
        ErlangComplianceTestEmitter.emit(ctx, service);
        ErlangEventStreamEmitter.emit(ctx, service);
        BeamResourceIndex resourceIndex = BeamResourceIndex.of(ctx.model());
        for (ResourceShape resource : resourceIndex.containedResourcesSorted(service)) {
            ErlangResourceEmitter.emitClient(ctx, resource);
        }

        ctx.writerDelegator().useFileWriter(ctx.definitionFile(), writer -> {
            writer.pushOperationBodySection();
            writer.write("%% Service closure: $L", service.getId());
            writer.write(
                    "%% Client configuration is intentionally opaque at this layer; "
                            + "endpoint, transport, and protocol live in future runtime modules.");
            writer.write("-type client_config() :: #{binary() => term()}.");
            writer.write("");
            BeamAwsServiceMetadata.from(service).ifPresent(meta -> {
                writer.write("%% AWS service metadata from model:");
                writer.write("%%   sdkId: $L", meta.sdkId());
                writer.write("%%   endpointPrefix: $L", meta.endpointPrefix());
                BeamSigV4Index sigv4Index = BeamSigV4Index.of(ctx.model(), service);
                List<OperationShape> unsignedOps =
                        sigv4Index.operationsWithUnsignedPayload(ctx.model(), service);
                writer.write("default_config() ->");
                writer.write("    #{region => <<\"us-east-1\">>,");
                writer.write("      endpoint_prefix => <<\"$L\">>,", meta.endpointPrefix());
                if (unsignedOps.isEmpty()) {
                    writer.write("      signing_name => <<\"$L\">>}.", meta.signingName());
                } else {
                    writer.write("      signing_name => <<\"$L\">>,", meta.signingName());
                    for (int i = 0; i < unsignedOps.size(); i++) {
                        Symbol opSym = directive.symbolProvider().toSymbol(unsignedOps.get(i));
                        if (i == unsignedOps.size() - 1) {
                            writer.write("      {unsigned_payload, $L} => true}.", opSym.getName());
                        } else {
                            writer.write("      {unsigned_payload, $L} => true,", opSym.getName());
                        }
                    }
                }
                writer.write("");
            });
            writer.popState();
        });
    }

    @Override
    public void generateOperation(
            GenerateOperationDirective<ErlangContext, BeamSettings> directive) {
        ErlangContext ctx = directive.context();
        OperationShape op = directive.shape();
        SymbolProvider sp = directive.symbolProvider();
        Symbol opSym = sp.toSymbol(op);

        StructureShape input = ctx.model().expectShape(op.getInputShape(), StructureShape.class);
        StructureShape output = ctx.model().expectShape(op.getOutputShape(), StructureShape.class);
        Symbol inSym = sp.toSymbol(input);
        Symbol outSym = sp.toSymbol(output);

        BeamErlangLayout layout = new BeamErlangLayout(
                ctx.settings(), ctx.service().getId().getNamespace(), ctx.service().getId().getName());
        boolean hasProtocol = BeamProtocolSupport.hasWireCodegen(
                ctx.resolvedProtocolTraitId(), ctx.protocolCodegen(), ctx.integrations());
        boolean wrapWithRetry = BeamClientRetrySupport.operationHasRetryableErrors(ctx.model(), op);
        String retryModule = layout.retryModuleName();
        boolean paginated = BeamClientPaginationSupport.isPaginated(
                ctx.model(), ctx.service(), op);
        PaginationInfo paginationInfo = paginated
                ? BeamClientPaginationSupport.requirePaginationInfo(ctx.model(), ctx.service(), op)
                : null;

        BeamDocumentation.forShape(op).ifPresent(doc -> {
            ctx.writerDelegator().useFileWriter(ctx.definitionFile(), writer -> {
                writer.pushOperationBodySection();
                BeamDocumentation.writeErlangDoc(writer, doc);
                writer.popState();
            });
        });

        ctx.writerDelegator().useFileWriter(ctx.definitionFile(), writer -> {
            writer.pushOperationBodySection();
            String successReturnType;
            if (paginated && BeamClientPaginationSupport.hasItemsMember(paginationInfo)) {
                successReturnType = "["
                        + BeamClientPaginationSupport.itemsElementSymbol(
                                        ctx.model(), sp, paginationInfo)
                                .orElseThrow()
                                .getName()
                        + "]";
            } else if (paginated) {
                successReturnType = "[" + outSym.getName() + "]";
            } else {
                successReturnType = outSym.getName();
            }
            ErlangFormat.writeSpec(
                    writer,
                    opSym.getName()
                            + "(client_config(), "
                            + inSym.getName()
                            + ") -> {'ok', "
                            + successReturnType
                            + "} | {'error', term()}");
            if (hasProtocol) {
                if (paginated) {
                    ErlangClientPaginationEmitter.emitPaginatedOperation(
                            ctx,
                            ctx.service(),
                            op,
                            wrapWithRetry,
                            retryModule,
                            () -> emitDispatchBody(
                                    ctx,
                                    op,
                                    layout,
                                    wrapWithRetry,
                                    retryModule,
                                    paginated,
                                    writer,
                                    DispatchBodyMode.PAGINATED_PAGE),
                            writer);
                } else {
                    writer.write("$L(Config, Input) ->", opSym.getName());
                    writer.indent();
                    emitDispatchBody(
                            ctx,
                            op,
                            layout,
                            wrapWithRetry,
                            retryModule,
                            paginated,
                            writer,
                            DispatchBodyMode.SINGLE_PAGE);
                    writer.dedent();
                }
            } else {
                writer.write("$L(_Config, _Input) -> {error, not_implemented}.", opSym.getName());
            }
            writer.write("");
            writer.popState();
        });

        if (ctx.protocolCodegen() != null) {
            ctx.writerDelegator().useFileWriter(ctx.definitionFile(), writer -> {
                ctx.protocolCodegen().emitOperationBindings(ctx, ctx.service(), op);
                writer.pushOperationBodySection();
                writer.write("%% HTTP request bindings for $L:", op.getId());
                for (Map.Entry<String, HttpBinding> entry :
                        ctx.httpBindings().requestBindings(op).entrySet()) {
                    HttpBinding binding = entry.getValue();
                    writer.write("%%   $L @ $L", entry.getKey(), binding.getLocation());
                }
                writer.write("");
                writer.popState();
                for (ErlangIntegration integration : ctx.integrations()) {
                    integration.customizeProtocolSerialize(ctx, op, writer);
                }
                for (ErlangIntegration integration : ctx.integrations()) {
                    integration.customizeProtocolDeserialize(ctx, op, writer);
                }
            });
        }
    }

    @Override
    public void generateResource(
            GenerateResourceDirective<ErlangContext, BeamSettings> directive) {
        // Emitted from generateService for all contained resources.
    }

    @Override
    public void generateEnumShape(
            GenerateEnumDirective<ErlangContext, BeamSettings> directive) {
        // Handled by ErlangTypeGeneration.
    }

    @Override
    public void generateIntEnumShape(
            GenerateIntEnumDirective<ErlangContext, BeamSettings> directive) {
        // Handled by ErlangTypeGeneration.
    }

    @Override
    public void generateUnion(
            GenerateUnionDirective<ErlangContext, BeamSettings> directive) {
        // Handled by ErlangTypeGeneration.
    }

    @Override
    public void generateStructure(
            GenerateStructureDirective<ErlangContext, BeamSettings> directive) {
        // Handled by ErlangTypeGeneration.
    }

    @Override
    public void generateError(
            GenerateErrorDirective<ErlangContext, BeamSettings> directive) {
        // Handled by ErlangTypeGeneration.
    }

    private enum DispatchBodyMode {
        SINGLE_PAGE,
        PAGINATED_PAGE
    }

    private static void emitDispatchBody(
            ErlangContext ctx,
            OperationShape op,
            BeamErlangLayout layout,
            boolean wrapWithRetry,
            String retryModule,
            boolean paginated,
            ErlangWriter writer,
            DispatchBodyMode mode) {
        SymbolProvider sp = ctx.symbolProvider();
        Symbol opSym = sp.toSymbol(op);
        boolean sigv4 = BeamSigV4Metadata.from(ctx.service()).isPresent();
        String sigv4Module = layout.sigv4ModuleName();
        String codecModule =
                layout.clientCodecModuleName(ctx.resolvedProtocolTraitId(), ctx.integrations());

        if (mode == DispatchBodyMode.SINGLE_PAGE && wrapWithRetry) {
            writer.write("RetryOpts = maps:get(retry, Config, #{}),");
            writer.write("$L:with_retry(fun() ->", retryModule);
            writer.indent();
        }

        if (ErlangRestJson1Emitter.serviceHasHostLabelOperations(ctx.model(), ctx.service())
                || ErlangRestXmlEmitter.serviceEncodesWithConfig(ctx.model(), ctx.service())) {
            writer.write("Req = $L:encode_$L_request(Config, Input),",
                    codecModule, opSym.getName());
        } else {
            writer.write("Req = $L:encode_$L_request(Input),",
                    codecModule, opSym.getName());
        }
        if (sigv4) {
            writer.write("SignedReq = case maps:get(credentials, Config, undefined) of");
            writer.indent();
            writer.write("undefined -> Req;");
            writer.write("_ -> $L:sign(Config, $L, Req)", sigv4Module, opSym.getName());
            writer.dedent();
            writer.write("end,");
            writer.write("case $L:dispatch(Config, SignedReq) of",
                    layout.runtimeHttpModuleName());
        } else {
            writer.write("case $L:dispatch(Config, Req) of",
                    layout.runtimeHttpModuleName());
        }
        writer.indent();
        writer.write("{ok, Resp} ->");
        writer.indent();
        if (mode == DispatchBodyMode.SINGLE_PAGE) {
            writer.write("$L:decode_$L_response(Resp);",
                    codecModule, opSym.getName());
        } else if (paginated && wrapWithRetry) {
            writer.write("$L:decode_$L_response(Resp);",
                    codecModule, opSym.getName());
        } else {
            writer.write("case $L:decode_$L_response(Resp) of",
                    codecModule, opSym.getName());
            writer.indent();
            writer.write("{ok, Output} ->");
            writer.indent();
            PaginationInfo pi = BeamClientPaginationSupport.requirePaginationInfo(
                    ctx.model(), ctx.service(), op);
            StructureShape output = ctx.model().expectShape(op.getOutputShape(), StructureShape.class);
            StructureShape input = ctx.model().expectShape(op.getInputShape(), StructureShape.class);
            String inputRecord = ErlangClientPaginationEmitter.recordName(sp.toSymbol(input));
            String inputToken = ErlangClientPaginationEmitter.fieldName(sp, pi.getInputTokenMember());
            String outputTokenExpr = ErlangClientPaginationEmitter.recordAccess(
                    "Output", output, pi.getOutputTokenMemberPath(), ctx.model(), sp);
            List<MemberShape> itemsPath = pi.getItemsMemberPath();
            boolean hasItems = BeamClientPaginationSupport.hasItemsMember(pi);
            String itemsExpr = hasItems
                    ? ErlangClientPaginationEmitter.recordAccess(
                            "Output", output, itemsPath, ctx.model(), sp)
                    : null;
            ErlangClientPaginationEmitter.emitAccumulationAndRecursion(
                    writer,
                    opSym,
                    hasItems,
                    itemsExpr,
                    outputTokenExpr,
                    inputRecord,
                    inputToken);
            writer.dedent();
            writer.write("{error, Reason} ->");
            writer.indent();
            writer.write("{error, Reason}");
            writer.dedent();
            writer.dedent();
            writer.write("end;");
        }
        writer.dedent();
        writer.write("{error, Reason} ->");
        writer.indent();
        writer.write("{error, Reason}");
        writer.dedent();
        writer.dedent();

        if (mode == DispatchBodyMode.SINGLE_PAGE) {
            if (wrapWithRetry) {
                writer.write("end");
                writer.dedent();
                writer.write("end, RetryOpts).");
            } else {
                writer.write("end.");
            }
        } else if (!paginated || !wrapWithRetry) {
            writer.write("end.");
        }
    }
}
