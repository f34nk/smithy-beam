package io.smithy.beam.erlang.codegen;

import io.smithy.beam.erlang.codegen.sections.OperationSpecSection;
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
 * Erlang feature integration that emits a Dialyzer-friendly {@code -spec}
 * line for every client operation function.
 *
 * <p>The integration appends to {@link OperationSpecSection} — that section is
 * pushed unconditionally by {@code ErlangClientCodegen.generateService} just
 * above each generated {@code <op>(Client, Input)} clause, so this interceptor
 * fires once per operation regardless of protocol or auth scheme.
 *
 * <p>The same section is also pushed by {@code ErlangServerCodegen.generateOperation},
 * but the server-mode handler signature is {@code handle_<op>(Req, State)} and
 * the corresponding {@code -spec} is owned by the {@code ServerImplCallbackSection}
 * integration that emits the {@code -callback} line. To avoid emitting a
 * client-shaped spec into server-mode output we gate on the symbol provider's
 * service module suffix ({@code _client} vs {@code _server}) — the only stable
 * mode signal available through {@link ErlangContext}.
 *
 * <p>Spec shape per operation:
 *
 * <pre>{@code
 * -spec <fn>(Client :: map(), Input :: <input>()) ->
 *     {ok, <output>()} | {error, <err1>() | <err2>() | ...}.
 * }</pre>
 *
 * <p>The error union is derived from {@code op.getErrors(service)} — that call
 * already returns the combined operation-level and service-level errors, the
 * latter being mirrored onto every operation by
 * {@code BeamPreludeIntegration.preprocessModel} via
 * {@code BeamModelTransforms.copyServiceErrorsToOperations}. Operations with
 * no declared errors fall back to {@code term()} so the spec still type-checks
 * against the runtime's transport-error tuples (e.g. {@code {http_error, _}},
 * {@code {signing_error, _}}) emitted by the existing protocol integrations.
 *
 * <p>Input or output shapes resolving to {@code smithy.api#Unit} are emitted
 * as {@code term()} — the unit shape has no generated record type so a literal
 * type alias would not exist in the generated types include.
 */
public final class ErlangSpecIntegration implements ErlangIntegration {

    private static final ShapeId UNIT = ShapeId.from("smithy.api#Unit");

    @Override
    public List<? extends CodeInterceptor<? extends CodeSection, ErlangWriter>> interceptors(
            ErlangContext ctx) {
        if (!isClientMode(ctx)) {
            return List.of();
        }
        return List.of(
                CodeInterceptor.appender(OperationSpecSection.class, (writer, section) ->
                        emit(writer, ctx, section.operation())));
    }

    private static boolean isClientMode(ErlangContext ctx) {
        // ErlangSymbolProvider derives the service module name from the codegen
        // mode: <module>_client.erl for clients, <module>_server.erl for servers.
        // The provider may be wrapped by SymbolProvider.cache(...), so we
        // recover the mode from the symbol's name suffix rather than from the
        // provider's runtime type.
        String svcModule = ctx.symbolProvider().toSymbol(ctx.service()).getName();
        return svcModule.endsWith("_client");
    }

    private static void emit(ErlangWriter w, ErlangContext ctx, OperationShape op) {
        String fnName = CaseUtils.toSnakeCase(op.getId().getName());
        String inputType = typeRef(ctx, op.getInputShape());
        String outputType = typeRef(ctx, op.getOutputShape());
        String errorUnion = errorUnion(ctx, op);

        w.write("-spec $L(Client :: map(), Input :: $L) ->", fnName, inputType);
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
}
