package io.smithy.beam.erlang.server;

import io.smithy.beam.core.Mode;
import io.smithy.beam.erlang.codegen.ErlangContext;
import io.smithy.beam.erlang.codegen.ErlangIntegration;
import io.smithy.beam.erlang.codegen.ErlangWriter;
import io.smithy.beam.erlang.codegen.sections.ServerHandlerCallbackSection;
import io.smithy.beam.erlang.codegen.sections.ServerImplCallbackSection;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import software.amazon.smithy.codegen.core.Symbol;
import software.amazon.smithy.model.shapes.OperationShape;
import software.amazon.smithy.model.shapes.Shape;
import software.amazon.smithy.model.shapes.ShapeId;
import software.amazon.smithy.utils.CaseUtils;
import software.amazon.smithy.utils.CodeInterceptor;
import software.amazon.smithy.utils.CodeSection;

/**
 * Erlang feature integration that emits Dialyzer-friendly type information for
 * the generated server module. Counterpart to {@code ErlangSpecIntegration},
 * which owns the same job for client modules.
 *
 * <p>Two interceptors fire per server operation:
 *
 * <ul>
 *   <li>A {@link CodeInterceptor.Prepender Prepender} on
 *       {@link ServerHandlerCallbackSection} writes a {@code -spec} line
 *       immediately above each {@code handle_<op>(Req, State) ->} clause. The
 *       handler is protocol-agnostic (it receives a raw transport request and
 *       returns whatever the protocol's response shape is), so the spec
 *       intentionally uses {@code term()} for both arguments and the return
 *       payload — the precise types live in the impl callback (below).</li>
 *   <li>An {@link CodeInterceptor.Appender Appender} on
 *       {@link ServerImplCallbackSection} writes a {@code -callback} line
 *       declaring the contract that the {@code <module>_server_impl.erl}
 *       behaviour must satisfy: {@code <op>(Input :: <op>_input(), Context ::
 *       term()) -> {ok, <op>_output()} | {error, <error_union>()}}.</li>
 * </ul>
 *
 * <p>Type references in the {@code -callback} line use the same unqualified
 * convention as {@code ErlangSpecIntegration}'s client-side {@code -spec}
 * (e.g. {@code get_forecast_input()} rather than
 * {@code weather_server_types:get_forecast_input()}). The type aliases are
 * defined in {@code <module>_server_types.hrl} via {@link ErlangWriter#writeRecord};
 * they resolve at compile time without an explicit {@code -include}, matching
 * the existing convention used by the generated client module.
 *
 * <p>The error union is derived from {@code op.getErrors(service)} — that call
 * already returns the combined operation-level and service-level errors, the
 * latter being mirrored onto every operation by
 * {@code BeamPreludeIntegration.preprocessModel} via
 * {@code BeamModelTransforms.copyServiceErrorsToOperations}. Operations with
 * no declared errors fall back to {@code term()} so the spec still type-checks
 * against arbitrary impl-side error tuples.
 *
 * <p>Input or output shapes resolving to {@code smithy.api#Unit} are emitted
 * as {@code term()} — the unit shape has no generated record so a literal
 * type alias would not exist in the types include.
 *
 * <p>Guards on {@link Mode#SERVER} so it is safe to register in the shared
 * {@code ErlangIntegration} SPI file — when the client plugin runs the mode
 * will be {@link Mode#CLIENT} and this integration becomes a no-op.
 */
public final class ErlangServerCallbackIntegration implements ErlangIntegration {

    private static final ShapeId UNIT = ShapeId.from("smithy.api#Unit");

    @Override
    public List<? extends CodeInterceptor<? extends CodeSection, ErlangWriter>> interceptors(
            ErlangContext ctx) {
        if (ctx.settings().mode() != Mode.SERVER) {
            return Collections.emptyList();
        }
        return List.of(
                new HandlerSpecPrepender(),
                CodeInterceptor.appender(ServerImplCallbackSection.class, (writer, section) ->
                        emitCallback(writer, ctx, section.operation())));
    }

    private static void emitCallback(ErlangWriter w, ErlangContext ctx, OperationShape op) {
        String fnName = CaseUtils.toSnakeCase(op.getId().getName());
        String inputType = typeRef(ctx, op.getInputShape());
        String outputType = typeRef(ctx, op.getOutputShape());
        String errorUnion = errorUnion(ctx, op);

        // Blank line separates the -callback declaration from the preceding
        // function clause's terminating "." so the module reads cleanly.
        w.write("");
        w.write("-callback $L(Input :: $L, Context :: term()) ->", fnName, inputType);
        w.indent();
        w.write("{ok, $L} | {error, $L}.", outputType, errorUnion);
        w.dedent();
    }

    private static String typeRef(ErlangContext ctx, ShapeId shapeId) {
        if (UNIT.equals(shapeId)) {
            return "term()";
        }
        Shape shape = ctx.model().expectShape(shapeId);
        Symbol symbol = ctx.symbolProvider().toSymbol(shape);
        return symbol.getName() + "()";
    }

    private static String errorUnion(ErlangContext ctx, OperationShape op) {
        List<ShapeId> errors = op.getErrors(ctx.service());
        if (errors.isEmpty()) {
            return "term()";
        }
        return errors.stream()
                .map(id -> typeRef(ctx, id))
                .collect(Collectors.joining(" | "));
    }

    /**
     * Prepends a protocol-agnostic {@code -spec} above each
     * {@code handle_<op>(Req, State) ->} clause. Implemented as a named
     * inner class because {@link CodeInterceptor} provides a static
     * {@code appender} factory but no equivalent {@code prepender} factory.
     */
    private static final class HandlerSpecPrepender
            implements CodeInterceptor.Prepender<ServerHandlerCallbackSection, ErlangWriter> {

        @Override
        public Class<ServerHandlerCallbackSection> sectionType() {
            return ServerHandlerCallbackSection.class;
        }

        @Override
        public void prepend(ErlangWriter writer, ServerHandlerCallbackSection section) {
            String fnName = "handle_" + CaseUtils.toSnakeCase(section.operation().getId().getName());
            writer.write("-spec $L(Req :: term(), State :: term()) -> "
                    + "{ok, term()} | {error, term()}.", fnName);
        }
    }
}
