package io.smithy.beam.elixir.codegen;

import static org.assertj.core.api.Assertions.assertThat;

import io.smithy.beam.core.binding.EventStreamHelper;
import io.smithy.beam.elixir.codegen.CodegenTestSupport.Fixture;
import io.smithy.beam.elixir.codegen.sections.EventStreamSection;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import software.amazon.smithy.model.shapes.OperationShape;
import software.amazon.smithy.utils.CodeInterceptor;
import software.amazon.smithy.utils.CodeSection;

/**
 * Unit tests for {@link ElixirEventStreamIntegration}.
 *
 * <p>Drives the integration against a small {@code @streaming} model fixture
 * covering the three event-stream shapes (input-only, output-only, and a
 * non-streaming control operation) and asserts that the {@code <op>_stream/2},
 * {@code <op>_stream/3}, and per-event encode/decode dispatchers — together
 * with the matching {@code SMITHY_EVENT_STREAM} runtime dependency — are
 * emitted by the {@link EventStreamSection} interceptor.
 */
class ElixirEventStreamIntegrationTest {

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
        fx = CodegenTestSupport.fixture(MODEL, "test.eventstream#Svc", "Svc");
    }

    @Test
    void emitsStreamArity2AndArity3ForInputStreamOperation() {
        OperationShape op = fx.operation("test.eventstream#PublishEvents");
        ElixirWriter w = drive(op);

        String out = w.toString();
        assertThat(out)
                .contains("def publish_events_stream(client, %PublishEventsInput{} = input) do")
                .contains("publish_events_stream(client, input, %{})")
                .contains("def publish_events_stream(client, %PublishEventsInput{} = input, opts) do")
                .contains("SmithyEventStream.start_stream(client, input, Map.merge(opts, %{")
                .contains("encode_event: &encode_publish_events_event/1");
    }

    @Test
    void emitsEncodeDispatcherWithOneClausePerInputEventVariant() {
        OperationShape op = fx.operation("test.eventstream#PublishEvents");
        String out = drive(op).toString();

        assertThat(out)
                .contains("defp encode_publish_events_event(%MessageEvent{} = event) do")
                .contains("{\"message\", event}")
                .contains("defp encode_publish_events_event(%HeartbeatEvent{} = event) do")
                .contains("{\"heartbeat\", event}");
    }

    @Test
    void emitsDecodeDispatcherForOutputStreamOperation() {
        OperationShape op = fx.operation("test.eventstream#SubscribeToEvents");
        String out = drive(op).toString();

        assertThat(out)
                .contains("def subscribe_to_events_stream(client, %SubscribeToEventsInput{} = input, opts) do")
                .contains("decode_event: &decode_subscribe_to_events_event/2")
                .contains("defp decode_subscribe_to_events_event(\"notification\", _frame) do")
                .contains("{:ok, %NotificationEvent{}}");
    }

    @Test
    void emitsSpecAnnotationsAndRegistersSmithyEventStreamDependency() {
        OperationShape op = fx.operation("test.eventstream#PublishEvents");
        ElixirWriter w = drive(op);

        assertThat(w.toString())
                .contains("@spec publish_events_stream(map(), PublishEventsInput.t()) :: "
                        + "{:ok, SmithyEventStream.stream_ref()} | {:error, term()}")
                .contains("@spec publish_events_stream(map(), PublishEventsInput.t(), map()) :: "
                        + "{:ok, SmithyEventStream.stream_ref()} | {:error, term()}");
        assertThat(w.getDependencies())
                .anyMatch(d -> d.getProperty("resourcePath", String.class)
                        .map(p -> p.endsWith("/smithy_event_stream.ex"))
                        .orElse(false));
    }

    @Test
    void emitsNothingForOperationsWithoutEventStream() {
        OperationShape op = fx.operation("test.eventstream#GetItem");
        ElixirWriter w = drive(op);

        String out = w.toString();
        assertThat(out)
                .doesNotContain("_stream(")
                .doesNotContain("SmithyEventStream.start_stream(");
        assertThat(w.getDependencies())
                .noneMatch(d -> d.getProperty("resourcePath", String.class)
                        .map(p -> p.endsWith("/smithy_event_stream.ex"))
                        .orElse(false));
    }

    private static ElixirWriter drive(OperationShape op) {
        ElixirWriter w = CodegenTestSupport.writer("svc_client.ex");
        List<? extends CodeInterceptor<? extends CodeSection, ElixirWriter>> interceptors =
                new ElixirEventStreamIntegration().interceptors(fx.ctx());
        for (CodeInterceptor<? extends CodeSection, ElixirWriter> i : interceptors) {
            w.onSection(i);
        }
        if (!EventStreamHelper.hasEventStream(fx.ctx().model(), op)) {
            return w;
        }
        w.injectSection(new EventStreamSection(op));
        return w;
    }
}
