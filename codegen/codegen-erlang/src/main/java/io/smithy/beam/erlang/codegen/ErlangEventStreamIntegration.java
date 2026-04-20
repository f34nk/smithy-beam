package io.smithy.beam.erlang.codegen;

import io.smithy.beam.core.binding.EventStreamHelper;
import io.smithy.beam.erlang.codegen.sections.EventStreamSection;
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
 * Erlang feature integration that emits event-stream helpers
 * ({@code <op>_stream/2}, {@code <op>_stream/3}, and per-event
 * encode/decode dispatchers) for every operation that carries an
 * {@code @streaming} union on its input or output.
 *
 * <p>The integration appends to {@link EventStreamSection} — that section is
 * only pushed by {@code ErlangClientCodegen.generateService} when
 * {@link EventStreamHelper#hasEventStream} is true, so this interceptor never
 * has to gate on the trait itself.
 *
 * <p>The wrappers delegate to {@code smithy_event_stream:start_stream/3}
 * (see {@code runtime-erlang/client/smithy_event_stream.erl}). The runtime is
 * a Phase-2 stub returning {@code {error, not_implemented}}; the helpers
 * exist so that smoke tests against an event-stream model produce
 * compilable code, in line with the Phase-2 deliverable for §8.2.
 *
 * <p>The encode/decode dispatchers emit one clause per named event in the
 * streaming union (or a single clause for single-event streams). The actual
 * payload codec is intentionally left as a TODO — concrete framing lands in
 * Phase 3 alongside the in-process language runners.
 */
public final class ErlangEventStreamIntegration implements ErlangIntegration {

    @Override
    public List<? extends CodeInterceptor<? extends CodeSection, ErlangWriter>> interceptors(
            ErlangContext ctx) {
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

    private static void emit(ErlangWriter w,
                             ErlangContext ctx,
                             OperationShape op,
                             Optional<EventStreamInfo> in,
                             Optional<EventStreamInfo> out) {
        w.addDependency(ErlangDependency.SMITHY_EVENT_STREAM);

        String opFn = CaseUtils.toSnakeCase(op.getId().getName());
        StructureShape inputShape = ctx.model().expectShape(op.getInputShape(), StructureShape.class);
        String inputRecord = CaseUtils.toSnakeCase(inputShape.getId().getName());

        emitStreamArity2(w, opFn, inputRecord);
        emitStreamArity3(w, opFn, inputRecord, in.isPresent(), out.isPresent());

        in.ifPresent(info -> emitEncodeDispatcher(w, opFn, info));
        out.ifPresent(info -> emitDecodeDispatcher(w, opFn, info));

        w.addExport(opFn + "_stream", 2);
        w.addExport(opFn + "_stream", 3);
    }

    private static void emitStreamArity2(ErlangWriter w, String opFn, String inputRecord) {
        w.write("");
        w.write("%% Opens a bidirectional event stream for $L.", opFn);
        w.write("-spec $L_stream(Client :: map(), Input :: $L()) -> "
                + "{ok, smithy_event_stream:stream_ref()} | {error, term()}.", opFn, inputRecord);
        w.write("$L_stream(Client, Input) ->", opFn);
        w.indent();
        w.write("$L_stream(Client, Input, #{}).", opFn);
        w.dedent();
        w.write("");
    }

    private static void emitStreamArity3(ErlangWriter w,
                                         String opFn,
                                         String inputRecord,
                                         boolean hasInput,
                                         boolean hasOutput) {
        w.write("-spec $L_stream(Client :: map(), Input :: $L(), Opts :: map()) -> "
                + "{ok, smithy_event_stream:stream_ref()} | {error, term()}.", opFn, inputRecord);
        w.write("$L_stream(Client, Input, Opts) ->", opFn);
        w.indent();
        w.write("smithy_event_stream:start_stream(Client, Input, Opts#{");
        w.indent();
        if (hasInput && hasOutput) {
            w.write("encode_event => fun encode_$L_event/1,", opFn);
            w.write("decode_event => fun decode_$L_event/2", opFn);
        } else if (hasInput) {
            w.write("encode_event => fun encode_$L_event/1", opFn);
        } else {
            w.write("decode_event => fun decode_$L_event/2", opFn);
        }
        w.dedent();
        w.write("}).");
        w.dedent();
        w.write("");
    }

    private static void emitEncodeDispatcher(ErlangWriter w, String opFn, EventStreamInfo info) {
        Map<String, StructureShape> events = info.getEvents();
        w.write("%% @private");
        w.write("%% Wire-encodes one event of the $L input stream.", opFn);
        w.write("%% TODO: payload framing lands in Phase 3 alongside smithy_event_stream:start_stream/3.");
        if (events.isEmpty()) {
            w.write("encode_$L_event(_Event) ->", opFn);
            w.indent();
            w.write("{error, not_implemented}.");
            w.dedent();
            w.write("");
            return;
        }
        int i = 0;
        for (Map.Entry<String, StructureShape> entry : events.entrySet()) {
            String eventName = entry.getKey();
            String recordName = CaseUtils.toSnakeCase(entry.getValue().getId().getName());
            String tag = eventName;
            String terminator = (++i == events.size()) ? "." : ";";
            w.write("encode_$L_event(#$L{} = Event) ->", opFn, recordName);
            w.indent();
            w.write("{<<\"$L\">>, Event}$L", tag, terminator);
            w.dedent();
        }
        w.write("");
    }

    private static void emitDecodeDispatcher(ErlangWriter w, String opFn, EventStreamInfo info) {
        Map<String, StructureShape> events = info.getEvents();
        w.write("%% @private");
        w.write("%% Wire-decodes one event of the $L output stream.", opFn);
        w.write("%% TODO: payload framing lands in Phase 3 alongside smithy_event_stream:recv_event/1.");
        if (events.isEmpty()) {
            w.write("decode_$L_event(_Tag, _Frame) ->", opFn);
            w.indent();
            w.write("{error, not_implemented}.");
            w.dedent();
            w.write("");
            return;
        }
        int i = 0;
        for (Map.Entry<String, StructureShape> entry : events.entrySet()) {
            String eventName = entry.getKey();
            String recordName = CaseUtils.toSnakeCase(entry.getValue().getId().getName());
            String tag = eventName;
            String terminator = (++i == events.size()) ? "." : ";";
            w.write("decode_$L_event(<<\"$L\">>, _Frame) ->", opFn, tag);
            w.indent();
            w.write("{ok, #$L{}}$L", recordName, terminator);
            w.dedent();
        }
        w.write("");
    }
}
