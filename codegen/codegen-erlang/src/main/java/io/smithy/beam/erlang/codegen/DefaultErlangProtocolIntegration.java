package io.smithy.beam.erlang.codegen;

import io.smithy.beam.core.ProtocolResolver;
import io.smithy.beam.erlang.codegen.codec.ErlangCodec;
import io.smithy.beam.erlang.codegen.codec.ErlangTransport;
import io.smithy.beam.erlang.codegen.sections.OperationErrorSection;
import io.smithy.beam.erlang.codegen.sections.OperationRequestSection;
import io.smithy.beam.erlang.codegen.sections.OperationResponseSection;
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
     * {@link #transport()} to populate the operation request / response /
     * error sections of the generated client module.
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
                CodeInterceptor.appender(OperationRequestSection.class, (writer, section) ->
                        emitRequestHelper(writer, ctx, section.operation(), codec, transport)),
                CodeInterceptor.appender(OperationResponseSection.class, (writer, section) -> {
                    // Response handling is fully encoded inside make_<op>_request,
                    // emitted by the OperationRequestSection interceptor above.
                }),
                CodeInterceptor.appender(OperationErrorSection.class, (writer, section) ->
                        emitErrorParser(writer, ctx, section.operation(), codec)));
    }

    private static void emitRequestHelper(ErlangWriter w, ErlangContext ctx,
                                          OperationShape op,
                                          ErlangCodec codec, ErlangTransport transport) {
        StructureShape inputShape = ctx.model().expectShape(op.getInputShape(), StructureShape.class);
        String opName = CaseUtils.toSnakeCase(op.getId().getName());
        String inputRecord = CaseUtils.toSnakeCase(inputShape.getId().getName());

        w.write("");
        w.openBlock("make_$L_request(Client, Input) when is_record(Input, $L) ->", opName, inputRecord);
        transport.writeRequest(w, ctx, op);
        codec.writeRequestEncode(w, ctx, op);
        transport.writeResponse(w, ctx, op, () -> codec.writeResponseDecode(w, ctx, op));
        w.dedent();
        w.write("");
    }

    private static void emitErrorParser(ErlangWriter w, ErlangContext ctx,
                                        OperationShape op, ErlangCodec codec) {
        String opName = CaseUtils.toSnakeCase(op.getId().getName());
        w.write("");
        w.openBlock("parse_$L_error(StatusCode, Body) ->", opName);
        codec.writeErrorDecode(w, ctx, op);
        w.dedent();
        w.write("");
    }
}
