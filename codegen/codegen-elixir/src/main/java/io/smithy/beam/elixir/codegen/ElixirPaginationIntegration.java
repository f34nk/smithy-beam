package io.smithy.beam.elixir.codegen;

import io.smithy.beam.core.binding.PaginationHelper;
import io.smithy.beam.elixir.codegen.sections.PaginationHelperSection;
import java.util.List;
import software.amazon.smithy.model.shapes.OperationShape;
import software.amazon.smithy.model.shapes.StructureShape;
import software.amazon.smithy.utils.CaseUtils;
import software.amazon.smithy.utils.CodeInterceptor;
import software.amazon.smithy.utils.CodeSection;

/**
 * Elixir feature integration that emits a {@code <op>_stream/3} helper for
 * every operation that carries a {@code @paginated} trait.
 *
 * <p>The integration appends to {@link PaginationHelperSection} — that section
 * is only pushed by {@code ElixirClientCodegen.generateService} when
 * {@link PaginationHelper#isPaginated} is true, so this interceptor never has
 * to gate on the protocol or on the trait itself.
 *
 * <p>The generated helper delegates to {@code SmithyClient.stream/3} (see
 * {@code runtime-elixir/client/smithy_client.ex}), which already encapsulates
 * the {@code next_token}/{@code items} pagination loop. The wrapper simply
 * forwards the per-operation {@code <op>_op/1} struct that the protocol
 * integration emitted alongside the main client function.
 */
public final class ElixirPaginationIntegration implements ElixirIntegration {

    @Override
    public List<? extends CodeInterceptor<? extends CodeSection, ElixirWriter>> interceptors(
            ElixirContext ctx) {
        return List.of(
                CodeInterceptor.appender(PaginationHelperSection.class, (writer, section) -> {
                    if (PaginationHelper.isPaginated(ctx.model(), ctx.service(), section.operation())) {
                        emit(writer, ctx, section.operation());
                    }
                }));
    }

    private static void emit(ElixirWriter w, ElixirContext ctx, OperationShape op) {
        w.addDependency(ElixirDependency.SMITHY_CLIENT);

        String fnName = CaseUtils.toSnakeCase(op.getId().getName());
        StructureShape inputShape = ctx.model().expectShape(op.getInputShape(), StructureShape.class);
        String inputStruct = inputShape.getId().getName();

        w.write("");
        w.write("@spec $L_stream(map(), $L.t(), map()) :: Enumerable.t()", fnName, inputStruct);
        w.write("def $L_stream(client, %$L{} = input, opts \\\\ %{}) do", fnName, inputStruct);
        w.indent();
        w.write("SmithyClient.stream(client, $L_op(input), opts)", fnName);
        w.dedent();
        w.write("end");
    }
}
