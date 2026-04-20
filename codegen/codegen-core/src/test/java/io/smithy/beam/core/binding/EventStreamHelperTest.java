package io.smithy.beam.core.binding;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.knowledge.EventStreamInfo;
import software.amazon.smithy.model.shapes.OperationShape;
import software.amazon.smithy.model.shapes.ShapeId;

class EventStreamHelperTest {

    private static Model model;
    private static OperationShape inputStreamOp;
    private static OperationShape outputStreamOp;
    private static OperationShape noStreamOp;

    @BeforeAll
    static void buildModel() {
        model = Model.assembler()
            .addUnparsedModel("event-stream-test.smithy", String.join("\n",
                "$version: \"2\"",
                "namespace com.example",
                "",
                "service EventService {",
                "    version: \"2024-01-01\"",
                "    operations: [PublishEvents, SubscribeToEvents, GetItem]",
                "}",
                "",
                "@streaming",
                "union InputEventStream {",
                "    message: MessageEvent",
                "}",
                "",
                "@streaming",
                "union OutputEventStream {",
                "    notification: NotificationEvent",
                "}",
                "",
                "structure MessageEvent { data: String }",
                "structure NotificationEvent { kind: String }",
                "",
                "operation PublishEvents {",
                "    input: PublishEventsInput",
                "    output: PublishEventsOutput",
                "}",
                "",
                "structure PublishEventsInput {",
                "    events: InputEventStream",
                "}",
                "",
                "structure PublishEventsOutput { ok: Boolean }",
                "",
                "operation SubscribeToEvents {",
                "    input: SubscribeToEventsInput",
                "    output: SubscribeToEventsOutput",
                "}",
                "",
                "structure SubscribeToEventsInput { filter: String }",
                "",
                "structure SubscribeToEventsOutput {",
                "    events: OutputEventStream",
                "}",
                "",
                "operation GetItem {",
                "    input: GetItemInput",
                "    output: GetItemOutput",
                "}",
                "",
                "structure GetItemInput { id: String }",
                "structure GetItemOutput { result: String }"
            ))
            .assemble()
            .unwrap();

        inputStreamOp  = model.expectShape(ShapeId.from("com.example#PublishEvents"),       OperationShape.class);
        outputStreamOp = model.expectShape(ShapeId.from("com.example#SubscribeToEvents"),   OperationShape.class);
        noStreamOp     = model.expectShape(ShapeId.from("com.example#GetItem"),             OperationShape.class);
    }

    @Test
    void inputReturnsPresentForOperationWithInputEventStream() {
        Optional<EventStreamInfo> info = EventStreamHelper.input(model, inputStreamOp);
        assertThat(info).isPresent();
    }

    @Test
    void inputEventStreamMemberNameIsCorrect() {
        EventStreamInfo info = EventStreamHelper.input(model, inputStreamOp).orElseThrow();
        assertThat(info.getEventStreamMember().getMemberName()).isEqualTo("events");
    }

    @Test
    void inputReturnsEmptyForOperationWithoutInputEventStream() {
        Optional<EventStreamInfo> info = EventStreamHelper.input(model, outputStreamOp);
        assertThat(info).isEmpty();
    }

    @Test
    void outputReturnsPresentForOperationWithOutputEventStream() {
        Optional<EventStreamInfo> info = EventStreamHelper.output(model, outputStreamOp);
        assertThat(info).isPresent();
    }

    @Test
    void outputEventStreamMemberNameIsCorrect() {
        EventStreamInfo info = EventStreamHelper.output(model, outputStreamOp).orElseThrow();
        assertThat(info.getEventStreamMember().getMemberName()).isEqualTo("events");
    }

    @Test
    void outputReturnsEmptyForOperationWithoutOutputEventStream() {
        Optional<EventStreamInfo> info = EventStreamHelper.output(model, inputStreamOp);
        assertThat(info).isEmpty();
    }

    @Test
    void hasEventStreamReturnsTrueForInputStreamOperation() {
        assertThat(EventStreamHelper.hasEventStream(model, inputStreamOp)).isTrue();
    }

    @Test
    void hasEventStreamReturnsTrueForOutputStreamOperation() {
        assertThat(EventStreamHelper.hasEventStream(model, outputStreamOp)).isTrue();
    }

    @Test
    void hasEventStreamReturnsFalseForNonStreamOperation() {
        assertThat(EventStreamHelper.hasEventStream(model, noStreamOp)).isFalse();
    }

    @Test
    void outputEventStreamEventsAreNonEmpty() {
        EventStreamInfo info = EventStreamHelper.output(model, outputStreamOp).orElseThrow();
        assertThat(info.getEvents()).containsKey("notification");
    }
}
