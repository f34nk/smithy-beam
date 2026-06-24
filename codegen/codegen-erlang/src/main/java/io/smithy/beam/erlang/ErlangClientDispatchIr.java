package io.smithy.beam.erlang;

import io.smithy.beam.core.BeamErlangLayout;
import io.smithy.beam.ir.erlang.ErlFunction;
import software.amazon.smithy.model.shapes.OperationShape;

import java.util.function.Consumer;

final class ErlangClientDispatchIr {
    private ErlangClientDispatchIr() {}

    static ErlFunction operationBody(
            ErlangContext ctx,
            OperationShape op,
            BeamErlangLayout layout,
            boolean wrapWithRetry,
            String retryModule,
            boolean paginated,
            ErlangClientDispatchEmitter.DispatchBodyMode mode) {
        return capture(writer -> ErlangClientDispatchEmitter.emitDispatchBody(
                ctx, op, layout, wrapWithRetry, retryModule, paginated, writer, mode));
    }

    static void writeBody(ErlangWriter writer, ErlFunction body) {
        for (String line : body.asString().lines().toList()) {
            writer.write(line);
        }
    }

    private static ErlFunction capture(Consumer<ErlangWriter> action) {
        ErlangWriter writer = new ErlangWriter("capture.erl");
        action.accept(writer);
        return ErlFunction.rendered(writer.toString().strip());
    }
}
