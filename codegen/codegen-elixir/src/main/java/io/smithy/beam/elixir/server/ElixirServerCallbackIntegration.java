package io.smithy.beam.elixir.server;

import io.smithy.beam.core.Mode;
import io.smithy.beam.elixir.codegen.ElixirContext;
import io.smithy.beam.elixir.codegen.ElixirIntegration;
import io.smithy.beam.elixir.codegen.ElixirWriter;
import io.smithy.beam.elixir.codegen.sections.ServerHandlerCallbackSection;
import io.smithy.beam.elixir.codegen.sections.ServerImplCallbackSection;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import software.amazon.smithy.model.shapes.OperationShape;
import software.amazon.smithy.model.shapes.ShapeId;
import software.amazon.smithy.utils.CaseUtils;
import software.amazon.smithy.utils.CodeInterceptor;
import software.amazon.smithy.utils.CodeSection;

/**
 * Elixir feature integration that emits Dialyzer-friendly type information for
 * the generated server module. Counterpart to {@code ElixirSpecIntegration},
 * which owns the same job for client modules.
 *
 * <p>Two interceptors fire per server operation:
 *
 * <ul>
 *   <li>A {@link CodeInterceptor.Prepender Prepender} on
 *       {@link ServerHandlerCallbackSection} writes an {@code @spec} line
 *       immediately above each {@code def handle_<op>(request, state)} clause.
 *       The handler is protocol-agnostic (it receives a raw transport request
 *       and returns whatever the protocol's response shape is), so the spec
 *       intentionally uses {@code term()} for both arguments and the return
 *       payload — the precise types live in the impl callback (below).</li>
 *   <li>An {@link CodeInterceptor.Appender Appender} on
 *       {@link ServerImplCallbackSection} writes an {@code @callback} line
 *       declaring the contract that the {@code <Namespace>.Server.Impl}
 *       behaviour must satisfy: {@code <op>(input :: <Op>Input.t(),
 *       context :: term()) :: {:ok, <Op>Output.t()} | {:error, <error_union>}}.</li>
 * </ul>
 *
 * <p>Type references in the {@code @callback} line use the same unqualified
 * convention as {@code ElixirSpecIntegration}'s client-side {@code @spec}
 * (e.g. {@code GetForecastInput.t()} rather than the fully-qualified
 * {@code Weather.Server.Types.GetForecastInput.t()}). Elixir is lenient about
 * unresolved type names in {@code @spec}/{@code @callback} attributes so this
 * matches the convention already used elsewhere in the generated output.
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
 * as {@code term()} — the unit shape has no generated module so a literal
 * {@code .t()} type would not exist.
 *
 * <p>Guards on {@link Mode#SERVER} so it is safe to register in the shared
 * {@code ElixirIntegration} SPI file — when the client plugin runs the mode
 * will be {@link Mode#CLIENT} and this integration becomes a no-op.
 */
public final class ElixirServerCallbackIntegration implements ElixirIntegration {

    private static final ShapeId UNIT = ShapeId.from("smithy.api#Unit");

    @Override
    public List<? extends CodeInterceptor<? extends CodeSection, ElixirWriter>> interceptors(
            ElixirContext ctx) {
        if (ctx.settings().mode() != Mode.SERVER) {
            return Collections.emptyList();
        }
        return List.of(
                new HandlerSpecPrepender(),
                CodeInterceptor.appender(ServerImplCallbackSection.class, (writer, section) ->
                        emitCallback(writer, ctx, section.operation())));
    }

    private static void emitCallback(ElixirWriter w, ElixirContext ctx, OperationShape op) {
        String fnName = CaseUtils.toSnakeCase(op.getId().getName());
        String inputType = typeRef(op.getInputShape());
        String outputType = typeRef(op.getOutputShape());
        String errorUnion = errorUnion(ctx, op);

        // Blank line separates the @callback declaration from the preceding
        // function clause's `end` so the module reads cleanly.
        w.write("");
        w.write("@callback $L(input :: $L, context :: term()) ::", fnName, inputType);
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
                .map(id -> typeRef(id))
                .collect(Collectors.joining(" | "));
    }

    /**
     * Prepends a protocol-agnostic {@code @spec} above each
     * {@code def handle_<op>(request, state)} clause. Implemented as a named
     * inner class because {@link CodeInterceptor} provides a static
     * {@code appender} factory but no equivalent {@code prepender} factory.
     */
    private static final class HandlerSpecPrepender
            implements CodeInterceptor.Prepender<ServerHandlerCallbackSection, ElixirWriter> {

        @Override
        public Class<ServerHandlerCallbackSection> sectionType() {
            return ServerHandlerCallbackSection.class;
        }

        @Override
        public void prepend(ElixirWriter writer, ServerHandlerCallbackSection section) {
            String fnName = "handle_" + CaseUtils.toSnakeCase(section.operation().getId().getName());
            writer.write("@spec $L(request :: term(), state :: term()) :: "
                    + "{:ok, term()} | {:error, term()}", fnName);
        }
    }
}
