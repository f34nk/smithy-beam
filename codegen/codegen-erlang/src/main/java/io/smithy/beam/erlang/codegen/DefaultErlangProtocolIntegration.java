package io.smithy.beam.erlang.codegen;

import io.smithy.beam.core.ProtocolResolver;
import io.smithy.beam.erlang.codegen.codec.ErlangCodec;
import io.smithy.beam.erlang.codegen.codec.ErlangTransport;
import io.smithy.beam.erlang.codegen.sections.OperationRequestSection;
import io.smithy.beam.erlang.codegen.sections.OperationResponseSection;
import io.smithy.beam.erlang.codegen.sections.OperationSendSection;
import io.smithy.beam.erlang.codegen.sections.ServiceErrorHelpersSection;
import java.util.Collections;
import java.util.List;
import software.amazon.smithy.model.shapes.OperationShape;
import software.amazon.smithy.model.shapes.ShapeId;
import software.amazon.smithy.model.shapes.StructureShape;
import software.amazon.smithy.utils.CaseUtils;
import software.amazon.smithy.utils.CodeInterceptor;
import software.amazon.smithy.utils.CodeSection;

/**
 * Base class for all Erlang protocol integrations.
 *
 * <p>Subclasses declare a {@link #protocolId()} together with the protocol's
 * {@link ErlangCodec} ({@link #codec()}) and {@link ErlangTransport}
 * ({@link #transport()}) strategy objects. The base class is responsible for
 * gating its own customisations with {@link #isApplicable(ErlangContext)} and
 * for wiring section-level interceptors that compose the codec and transport
 * outputs into a coherent emit. Subclasses contain no protocol logic
 * themselves — they merely select the codec/transport pair.
 */
public abstract class DefaultErlangProtocolIntegration implements ErlangIntegration {

    /** Returns the Smithy protocol shape ID this integration handles. */
    public abstract ShapeId protocolId();

    /** Returns the codec that drives request/response/error body serialisation. */
    protected abstract ErlangCodec codec();

    /** Returns the transport that drives URI/header/dispatch emission. */
    protected abstract ErlangTransport transport();

    /**
     * Returns {@code true} if the service being generated uses this integration's
     * protocol.
     */
    protected boolean isApplicable(ErlangContext ctx) {
        return ProtocolResolver.resolve(protocolId())
                .map(traitClass -> ctx.service().hasTrait(traitClass))
                .orElse(false);
    }

    /**
     * Registers section interceptors that delegate to {@link #codec()} and
     * {@link #transport()} to populate the public operation send body, the
     * internal {@code make_<op>_request/2} helper, and the module-level
     * {@code parse_error/2} error parser.
     *
     * <p>The {@link OperationSendSection} interceptor <strong>replaces</strong>
     * the default {@code {error, not_implemented}.} stub written by
     * {@link io.smithy.beam.erlang.client.ErlangClientCodegen} with the
     * retry-wrapped dispatch to {@code make_<op>_request/2}. All other
     * interceptors append into empty sections.
     */
    @Override
    public List<? extends CodeInterceptor<? extends CodeSection, ErlangWriter>> interceptors(
            ErlangContext ctx) {
        if (!isApplicable(ctx)) {
            return Collections.emptyList();
        }
        ErlangCodec codec = codec();
        ErlangTransport transport = transport();
        return List.of(
                replacer(OperationSendSection.class, (writer, section) ->
                        emitSendBody(writer, section.operation())),
                CodeInterceptor.appender(OperationRequestSection.class, (writer, section) ->
                        emitRequestHelper(writer, ctx, section.operation(), codec, transport)),
                CodeInterceptor.appender(OperationResponseSection.class, (writer, section) -> {
                    // Response handling is fully encoded inside make_<op>_request,
                    // emitted by the OperationRequestSection interceptor above.
                }),
                CodeInterceptor.appender(ServiceErrorHelpersSection.class, (writer, section) ->
                        emitParseError(writer, ctx, codec)));
    }

    /**
     * Creates an interceptor that drops the previously written default text
     * and emits fresh content in its place.
     */
    private static <S extends CodeSection> CodeInterceptor<S, ErlangWriter> replacer(
            Class<S> type,
            java.util.function.BiConsumer<ErlangWriter, S> body) {
        return new CodeInterceptor<S, ErlangWriter>() {
            @Override
            public Class<S> sectionType() {
                return type;
            }

            @Override
            public void write(ErlangWriter writer, String previousText, S section) {
                body.accept(writer, section);
            }
        };
    }

    private static void emitSendBody(ErlangWriter w, OperationShape op) {
        String opName = CaseUtils.toSnakeCase(op.getId().getName());
        w.addDependency(ErlangDependency.SMITHY_RETRY);
        w.write("    RequestFun = fun() -> make_$L_request(Client, Input) end,", opName);
        w.write("    case maps:get(enable_retry, Options, true) of");
        w.write("        true -> smithy_retry:with_retry(RequestFun, Options);");
        w.write("        false -> RequestFun()");
        w.write("    end.");
    }

    private static void emitRequestHelper(ErlangWriter w, ErlangContext ctx,
                                          OperationShape op,
                                          ErlangCodec codec, ErlangTransport transport) {
        StructureShape inputShape = ctx.model().expectShape(op.getInputShape(), StructureShape.class);
        StructureShape outputShape = ctx.model().expectShape(op.getOutputShape(), StructureShape.class);
        String opName = CaseUtils.toSnakeCase(op.getId().getName());
        String inputType = CaseUtils.toSnakeCase(inputShape.getId().getName());
        String outputType = CaseUtils.toSnakeCase(outputShape.getId().getName());
        String inputRecord = inputType;

        w.write("-spec make_$L_request(Client :: map(), Input :: $L()) ->", opName, inputType);
        w.write("    {ok, $L()} | {error, term()}.", outputType);
        w.openBlock("make_$L_request(Client, Input) when is_record(Input, $L) ->", opName, inputRecord);
        transport.writeRequest(w, ctx, op);
        codec.writeRequestEncode(w, ctx, op);
        transport.writeResponse(w, ctx, op, () -> codec.writeResponseDecode(w, ctx, op));
        w.dedent();
        w.write("");
    }

    private static void emitParseError(ErlangWriter w, ErlangContext ctx, ErlangCodec codec) {
        w.write("");
        w.write("-spec parse_error(StatusCode :: non_neg_integer(), Body :: binary()) ->");
        w.write("    {error, term()}.");
        w.openBlock("parse_error(StatusCode, Body) ->");
        codec.writeErrorDecode(w, ctx, null);
        w.dedent();
        w.addExport("parse_error", 2);
    }
}
