package io.smithy.beam.elixir.codegen;

import io.smithy.beam.elixir.codegen.sections.OperationSpecSection;
import java.util.List;
import java.util.stream.Collectors;
import software.amazon.smithy.model.shapes.OperationShape;
import software.amazon.smithy.model.shapes.ShapeId;
import software.amazon.smithy.utils.CaseUtils;
import software.amazon.smithy.utils.CodeInterceptor;
import software.amazon.smithy.utils.CodeSection;

/**
 * Elixir feature integration that emits a Dialyzer-friendly {@code @spec}
 * line for every client operation function.
 *
 * <p>The integration appends to {@link OperationSpecSection} — that section is
 * pushed unconditionally by {@code ElixirClientCodegen.generateService} just
 * above each generated {@code def <op>(config, input)} clause, so this
 * interceptor fires once per operation regardless of protocol or auth scheme.
 *
 * <p>The same section is also pushed by {@code ElixirServerCodegen.generateService},
 * but the server-mode handler signature is {@code handle_<op>(request, state)}
 * and the corresponding {@code @spec} is owned by the
 * {@code ServerImplCallbackSection} integration that emits the {@code @callback}
 * line. To avoid emitting a client-shaped spec into server-mode output we
 * gate on the symbol provider's service module suffix
 * ({@code .Client} vs {@code .Server}) — the only stable mode signal
 * available through {@link ElixirContext}.
 *
 * <p>Spec shape per operation:
 *
 * <pre>{@code
 * @spec <fn>(map(), <Input>.t()) ::
 *         {:ok, <Output>.t()} | {:error, <Err1>.t() | <Err2>.t() | ...}
 * }</pre>
 *
 * <p>The error union is derived from {@code op.getErrors(service)} — that call
 * already returns the combined operation-level and service-level errors, the
 * latter being mirrored onto every operation by
 * {@code BeamPreludeIntegration.preprocessModel} via
 * {@code BeamModelTransforms.copyServiceErrorsToOperations}. Operations with
 * no declared errors fall back to {@code term()} so the spec still type-checks
 * against the runtime's transport-error tuples (e.g. {@code {:http_error, _}},
 * {@code {:signing_error, _}}) emitted by the existing protocol integrations.
 *
 * <p>Input or output shapes resolving to {@code smithy.api#Unit} are emitted
 * as {@code term()} — the unit shape has no generated struct module so a
 * {@code .t()} type would not exist in the generated types module.
 *
 * <p>Type references use the local Smithy shape name (e.g.
 * {@code GetWeatherInput.t()}) rather than the fully-qualified Elixir module
 * name. This matches the convention established by the existing pagination,
 * waiter, and event-stream integrations and relies on the corresponding
 * {@code alias} statements emitted into the client module by
 * {@code ModuleAttributesSection} / its integrations.
 */
public final class ElixirSpecIntegration implements ElixirIntegration {

    private static final ShapeId UNIT = ShapeId.from("smithy.api#Unit");

    @Override
    public List<? extends CodeInterceptor<? extends CodeSection, ElixirWriter>> interceptors(
            ElixirContext ctx) {
        if (!isClientMode(ctx)) {
            return List.of();
        }
        return List.of(
                CodeInterceptor.appender(OperationSpecSection.class, (writer, section) ->
                        emit(writer, section.operation(), errorUnion(ctx, section.operation()))));
    }

    private static boolean isClientMode(ElixirContext ctx) {
        // ElixirSymbolProvider derives the service module name from the codegen
        // mode: "<Namespace>.Client" for clients, "<Namespace>.Server" for
        // servers. The provider may be wrapped by SymbolProvider.cache(...),
        // so we recover the mode from the symbol's name suffix rather than
        // from the provider's runtime type.
        String svcModule = ctx.symbolProvider().toSymbol(ctx.service()).getName();
        return svcModule.endsWith(".Client");
    }

    private static void emit(ElixirWriter w, OperationShape op, String errorUnion) {
        String fnName = CaseUtils.toSnakeCase(op.getId().getName());
        String inputType = typeRef(op.getInputShape());
        String outputType = typeRef(op.getOutputShape());

        w.write("@spec $L(map(), $L) ::", fnName, inputType);
        w.indent();
        w.write("{:ok, $L} | {:error, $L}", outputType, errorUnion);
        w.dedent();
    }

    private static String typeRef(ShapeId shapeId) {
        if (UNIT.equals(shapeId)) {
            return "term()";
        }
        return shapeId.getName() + ".t()";
    }

    private static String errorUnion(ElixirContext ctx, OperationShape op) {
        List<ShapeId> errors = op.getErrors(ctx.service());
        if (errors.isEmpty()) {
            return "term()";
        }
        return errors.stream()
                .map(ElixirSpecIntegration::typeRef)
                .collect(Collectors.joining(" | "));
    }
}
