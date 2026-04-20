package io.smithy.beam.elixir.codegen;

import io.smithy.beam.core.ProtocolResolver;
import io.smithy.beam.elixir.codegen.codec.ElixirCodec;
import io.smithy.beam.elixir.codegen.codec.ElixirTransport;
import io.smithy.beam.elixir.codegen.sections.OperationErrorSection;
import io.smithy.beam.elixir.codegen.sections.OperationRequestSection;
import io.smithy.beam.elixir.codegen.sections.OperationResponseSection;
import java.util.Collections;
import java.util.List;
import software.amazon.smithy.model.shapes.OperationShape;
import software.amazon.smithy.model.shapes.ShapeId;
import software.amazon.smithy.model.shapes.StructureShape;
import software.amazon.smithy.utils.CaseUtils;
import software.amazon.smithy.utils.CodeInterceptor;
import software.amazon.smithy.utils.CodeSection;

/**
 * Base class for all Elixir protocol integrations.
 *
 * <p>Subclasses declare a {@link #protocolId()} together with the protocol's
 * {@link ElixirCodec} ({@link #codec()}) and {@link ElixirTransport}
 * ({@link #transport()}) strategy objects. The base class is responsible for
 * gating its own customisations with {@link #isApplicable(ElixirContext)} and
 * for wiring section-level interceptors that compose the codec and transport
 * outputs into a coherent emit. Subclasses contain no protocol logic
 * themselves — they merely select the codec/transport pair.
 */
public abstract class DefaultElixirProtocolIntegration implements ElixirIntegration {

    /** Returns the Smithy protocol shape ID this integration handles. */
    public abstract ShapeId protocolId();

    /** Returns the codec that drives request/response/error body serialisation. */
    protected abstract ElixirCodec codec();

    /** Returns the transport that drives URI/header/dispatch emission. */
    protected abstract ElixirTransport transport();

    /**
     * Returns {@code true} if the service being generated uses this integration's
     * protocol.
     */
    protected boolean isApplicable(ElixirContext ctx) {
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
    public List<? extends CodeInterceptor<? extends CodeSection, ElixirWriter>> interceptors(
            ElixirContext ctx) {
        if (!isApplicable(ctx)) {
            return Collections.emptyList();
        }
        ElixirCodec codec = codec();
        ElixirTransport transport = transport();
        return List.of(
                CodeInterceptor.appender(OperationRequestSection.class, (writer, section) ->
                        emitOperationHelper(writer, ctx, section.operation(), codec, transport)),
                CodeInterceptor.appender(OperationResponseSection.class, (writer, section) -> {
                    // Response handling is fully encoded inside <op>_op,
                    // emitted by the OperationRequestSection interceptor above.
                }),
                CodeInterceptor.appender(OperationErrorSection.class, (writer, section) -> {
                    // parse_error_fn pointer is part of the Operation struct,
                    // emitted by the OperationRequestSection interceptor above.
                }));
    }

    private static void emitOperationHelper(ElixirWriter w, ElixirContext ctx,
                                            OperationShape op,
                                            ElixirCodec codec, ElixirTransport transport) {
        StructureShape inputShape = ctx.model().expectShape(op.getInputShape(), StructureShape.class);
        String opName = CaseUtils.toSnakeCase(op.getId().getName());
        String inputStruct = inputShape.getId().getName();
        String outputStruct = ctx.model()
                .expectShape(op.getOutputShape(), StructureShape.class)
                .getId().getName();
        String action = op.getId().getName();

        w.write("");
        w.openBlock("defp $L_op(%$L{} = input) do", opName, inputStruct);
        w.openBlock("%SmithyClient.Operation{");
        w.write("name: :$L,", opName);
        w.write("action: $S,", action);
        transport.writeRequest(w, ctx, op);
        w.write("input: input,");
        w.write("output_shape: $L,", outputStruct);
        w.write("auth: :sigv4,");
        codec.writeRequestEncode(w, ctx, op);
        transport.writeResponse(w, ctx, op, () -> codec.writeResponseDecode(w, ctx, op));
        codec.writeErrorDecode(w, ctx, op);
        w.closeBlock("}");
        w.closeBlock("end");
        w.write("");
    }
}
