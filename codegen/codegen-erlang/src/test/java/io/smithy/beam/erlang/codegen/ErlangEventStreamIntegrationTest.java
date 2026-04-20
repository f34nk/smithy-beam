package io.smithy.beam.erlang.codegen;

import static org.assertj.core.api.Assertions.assertThat;

import io.smithy.beam.core.binding.EventStreamHelper;
import io.smithy.beam.erlang.codegen.CodegenTestSupport.Fixture;
import io.smithy.beam.erlang.codegen.sections.EventStreamSection;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import software.amazon.smithy.model.shapes.OperationShape;
import software.amazon.smithy.utils.CodeInterceptor;
import software.amazon.smithy.utils.CodeSection;

/**
 * Unit tests for {@link ErlangEventStreamIntegration}.
 *
 * <p>Drives the integration against a small {@code @streaming} model fixture
 * covering the three event-stream shapes (input-only, output-only, and a
 * non-streaming control operation) and asserts that the {@code <op>_stream/2},
 * {@code <op>_stream/3}, and per-event encode/decode dispatchers — together
 * with the matching {@code SMITHY_EVENT_STREAM} runtime dependency — are
 * emitted by the {@link EventStreamSection} interceptor.
 */
class ErlangEventStreamIntegrationTest {

    private static final String MODEL = String.join("\n",
            "$version: \"2\"",
            "namespace test.eventstream",
            "",
            "service Svc {",
            "    version: \"2024\"",
            "    operations: [PublishEvents, SubscribeToEvents, GetItem]",
            "}",
            "",
            "@streaming",
            "union InputEventStream {",
            "    message: MessageEvent",
            "    heartbeat: HeartbeatEvent",
            "}",
            "",
            "@streaming",
            "union OutputEventStream {",
            "    notification: NotificationEvent",
            "}",
            "",
            "structure MessageEvent { data: String }",
            "structure HeartbeatEvent { ts: String }",
            "structure NotificationEvent { kind: String }",
            "",
            "operation PublishEvents {",
            "    input: PublishEventsInput",
            "    output: PublishEventsOutput",
            "}",
            "structure PublishEventsInput { events: InputEventStream }",
            "structure PublishEventsOutput { ok: Boolean }",
            "",
            "operation SubscribeToEvents {",
            "    input: SubscribeToEventsInput",
            "    output: SubscribeToEventsOutput",
            "}",
            "structure SubscribeToEventsInput { filter: String }",
            "structure SubscribeToEventsOutput { events: OutputEventStream }",
            "",
            "@readonly",
            "operation GetItem {",
            "    input: GetItemInput",
            "    output: GetItemOutput",
            "}",
            "structure GetItemInput { id: String }",
            "structure GetItemOutput { result: String }");

    private static Fixture fx;

    @BeforeAll
    static void setUp() {
        fx = CodegenTestSupport.fixture(MODEL, "test.eventstream#Svc", "svc");
    }

    @Test
    void emitsStreamArity2AndArity3ForInputStreamOperation() {
        OperationShape op = fx.operation("test.eventstream#PublishEvents");
        ErlangWriter w = drive(op);

        String out = w.toString();
        assertThat(out)
                .contains("publish_events_stream(Client, Input) ->")
                .contains("publish_events_stream(Client, Input, #{}).")
                .contains("publish_events_stream(Client, Input, Opts) ->")
                .contains("smithy_event_stream:start_stream(Client, Input, Opts#{")
                .contains("encode_event => fun encode_publish_events_event/1");
    }

    @Test
    void emitsEncodeDispatcherWithOneClausePerInputEventVariant() {
        OperationShape op = fx.operation("test.eventstream#PublishEvents");
        String out = drive(op).toString();

        assertThat(out)
                .contains("encode_publish_events_event(#message_event{} = Event) ->")
                .contains("{<<\"message\">>, Event}")
                .contains("encode_publish_events_event(#heartbeat_event{} = Event) ->")
                .contains("{<<\"heartbeat\">>, Event}");
    }

    @Test
    void emitsDecodeDispatcherForOutputStreamOperation() {
        OperationShape op = fx.operation("test.eventstream#SubscribeToEvents");
        String out = drive(op).toString();

        assertThat(out)
                .contains("subscribe_to_events_stream(Client, Input, Opts) ->")
                .contains("decode_event => fun decode_subscribe_to_events_event/2")
                .contains("decode_subscribe_to_events_event(<<\"notification\">>, _Frame) ->")
                .contains("{ok, #notification_event{}}");
    }

    @Test
    void exportsStreamFunctionsAndRegistersSmithyEventstreamDependency() {
        OperationShape op = fx.operation("test.eventstream#PublishEvents");
        ErlangWriter w = drive(op);

        assertThat(w.toString())
                .contains("publish_events_stream/2")
                .contains("publish_events_stream/3");
        assertThat(w.getDependencies())
                .anyMatch(d -> d.getProperty("resourcePath", String.class)
                        .map(p -> p.endsWith("/smithy_event_stream.erl"))
                        .orElse(false));
    }

    @Test
    void emitsNothingForOperationsWithoutEventStream() {
        OperationShape op = fx.operation("test.eventstream#GetItem");
        ErlangWriter w = drive(op);

        String out = w.toString();
        assertThat(out)
                .doesNotContain("_stream(")
                .doesNotContain("smithy_event_stream:start_stream(");
        assertThat(w.getDependencies())
                .noneMatch(d -> d.getProperty("resourcePath", String.class)
                        .map(p -> p.endsWith("/smithy_event_stream.erl"))
                        .orElse(false));
    }

    /**
     * Drives the event-stream section through a fresh writer with the
     * integration's interceptor attached. Mirrors the per-operation push
     * that {@code ErlangClientCodegen.generateService} performs.
     */
    private static ErlangWriter drive(OperationShape op) {
        ErlangWriter w = CodegenTestSupport.writer("svc_client.erl");
        List<? extends CodeInterceptor<? extends CodeSection, ErlangWriter>> interceptors =
                new ErlangEventStreamIntegration().interceptors(fx.ctx());
        for (CodeInterceptor<? extends CodeSection, ErlangWriter> i : interceptors) {
            w.onSection(i);
        }
        if (!EventStreamHelper.hasEventStream(fx.ctx().model(), op)) {
            return w;
        }
        w.injectSection(new EventStreamSection(op));
        return w;
    }
}
