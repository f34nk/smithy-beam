package io.smithy.beam.elixir.codegen;

import io.smithy.beam.core.binding.EventStreamHelper;
import io.smithy.beam.elixir.codegen.sections.EventStreamSection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import software.amazon.smithy.model.knowledge.EventStreamInfo;
import software.amazon.smithy.model.shapes.OperationShape;
import software.amazon.smithy.model.shapes.StructureShape;
import software.amazon.smithy.utils.CaseUtils;
import software.amazon.smithy.utils.CodeInterceptor;
import software.amazon.smithy.utils.CodeSection;

/**
 * Elixir feature integration that emits event-stream helpers
 * ({@code <op>_stream/2}, {@code <op>_stream/3}, and per-event
 * encode/decode dispatchers) for every operation that carries an
 * {@code @streaming} union on its input or output.
 *
 * <p>The integration appends to {@link EventStreamSection} — that section is
 * only pushed by {@code ElixirClientCodegen.generateService} when
 * {@link EventStreamHelper#hasEventStream} is true, so this interceptor never
 * has to gate on the trait itself.
 *
 * <p>The wrappers delegate to {@code SmithyEventStream.start_stream/3}
 * (see {@code runtime-elixir/client/smithy_event_stream.ex}). The runtime is
 * a stub returning {@code {:error, :not_implemented}}; the helpers exist so
 * that smoke tests against an event-stream model produce compilable code.
 *
 * <p>The encode/decode dispatchers emit one clause per named event in the
 * streaming union (or a single clause for single-event streams). The actual
 * payload codec is intentionally left as a TODO — concrete framing is not
 * yet implemented.
 */
public final class ElixirEventStreamIntegration implements ElixirIntegration {

    @Override
    public List<? extends CodeInterceptor<? extends CodeSection, ElixirWriter>> interceptors(
            ElixirContext ctx) {
        return List.of(
                CodeInterceptor.appender(EventStreamSection.class, (writer, section) -> {
                    OperationShape op = section.operation();
                    Optional<EventStreamInfo> in = EventStreamHelper.input(ctx.model(), op);
                    Optional<EventStreamInfo> out = EventStreamHelper.output(ctx.model(), op);
                    if (in.isEmpty() && out.isEmpty()) {
                        return;
                    }
                    emit(writer, ctx, op, in, out);
                }));
    }

    private static void emit(ElixirWriter w,
                             ElixirContext ctx,
                             OperationShape op,
                             Optional<EventStreamInfo> in,
                             Optional<EventStreamInfo> out) {
        w.addDependency(ElixirDependency.SMITHY_EVENT_STREAM);

        String opFn = CaseUtils.toSnakeCase(op.getId().getName());
        StructureShape inputShape = ctx.model().expectShape(op.getInputShape(), StructureShape.class);
        String inputStruct = inputShape.getId().getName();

        emitStreamArity2(w, opFn, inputStruct);
        emitStreamArity3(w, opFn, inputStruct, in.isPresent(), out.isPresent());

        in.ifPresent(info -> emitEncodeDispatcher(w, opFn, info));
        out.ifPresent(info -> emitDecodeDispatcher(w, opFn, info));
    }

    private static void emitStreamArity2(ElixirWriter w, String opFn, String inputStruct) {
        w.write("");
        w.write("@doc \"Opens a bidirectional event stream for $L.\"", opFn);
        w.write("@spec $L_stream(map(), $L.t()) :: "
                + "{:ok, SmithyEventStream.stream_ref()} | {:error, term()}", opFn, inputStruct);
        w.write("def $L_stream(client, %$L{} = input) do", opFn, inputStruct);
        w.indent();
        w.write("$L_stream(client, input, %{})", opFn);
        w.dedent();
        w.write("end");
        w.write("");
    }

    private static void emitStreamArity3(ElixirWriter w,
                                         String opFn,
                                         String inputStruct,
                                         boolean hasInput,
                                         boolean hasOutput) {
        w.write("@spec $L_stream(map(), $L.t(), map()) :: "
                + "{:ok, SmithyEventStream.stream_ref()} | {:error, term()}", opFn, inputStruct);
        w.write("def $L_stream(client, %$L{} = input, opts) do", opFn, inputStruct);
        w.indent();
        w.write("SmithyEventStream.start_stream(client, input, Map.merge(opts, %{");
        w.indent();
        if (hasInput && hasOutput) {
            w.write("encode_event: &encode_$L_event/1,", opFn);
            w.write("decode_event: &decode_$L_event/2", opFn);
        } else if (hasInput) {
            w.write("encode_event: &encode_$L_event/1", opFn);
        } else {
            w.write("decode_event: &decode_$L_event/2", opFn);
        }
        w.dedent();
        w.write("}))");
        w.dedent();
        w.write("end");
        w.write("");
    }

    private static void emitEncodeDispatcher(ElixirWriter w, String opFn, EventStreamInfo info) {
        Map<String, StructureShape> events = info.getEvents();
        w.write("# Wire-encodes one event of the $L input stream.", opFn);
        w.write("# TODO: payload framing not yet implemented — see SmithyEventStream.start_stream/3.");
        if (events.isEmpty()) {
            w.write("defp encode_$L_event(_event), do: {:error, :not_implemented}", opFn);
            w.write("");
            return;
        }
        for (Map.Entry<String, StructureShape> entry : events.entrySet()) {
            String eventName = entry.getKey();
            String structName = entry.getValue().getId().getName();
            w.write("defp encode_$L_event(%$L{} = event) do", opFn, structName);
            w.indent();
            w.write("{\"$L\", event}", eventName);
            w.dedent();
            w.write("end");
        }
        w.write("");
    }

    private static void emitDecodeDispatcher(ElixirWriter w, String opFn, EventStreamInfo info) {
        Map<String, StructureShape> events = info.getEvents();
        w.write("# Wire-decodes one event of the $L output stream.", opFn);
        w.write("# TODO: payload framing not yet implemented — see SmithyEventStream.recv_event/1.");
        if (events.isEmpty()) {
            w.write("defp decode_$L_event(_tag, _frame), do: {:error, :not_implemented}", opFn);
            w.write("");
            return;
        }
        for (Map.Entry<String, StructureShape> entry : events.entrySet()) {
            String eventName = entry.getKey();
            String structName = entry.getValue().getId().getName();
            w.write("defp decode_$L_event(\"$L\", _frame) do", opFn, eventName);
            w.indent();
            w.write("{:ok, %$L{}}", structName);
            w.dedent();
            w.write("end");
        }
        w.write("");
    }
}
