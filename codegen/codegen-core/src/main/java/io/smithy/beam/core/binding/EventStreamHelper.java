package io.smithy.beam.core.binding;

import java.util.Optional;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.knowledge.EventStreamIndex;
import software.amazon.smithy.model.knowledge.EventStreamInfo;
import software.amazon.smithy.model.shapes.OperationShape;

/**
 * Thin static facade over {@link EventStreamIndex} for the language-specific
 * codegen modules. No language-specific logic is present here.
 */
public final class EventStreamHelper {

    private EventStreamHelper() {}

    /**
     * Returns the {@link EventStreamInfo} for the operation's <em>input</em>
     * event stream, or empty if the input does not contain an event stream.
     */
    public static Optional<EventStreamInfo> input(Model model, OperationShape op) {
        return EventStreamIndex.of(model).getInputInfo(op);
    }

    /**
     * Returns the {@link EventStreamInfo} for the operation's <em>output</em>
     * event stream, or empty if the output does not contain an event stream.
     */
    public static Optional<EventStreamInfo> output(Model model, OperationShape op) {
        return EventStreamIndex.of(model).getOutputInfo(op);
    }

    /**
     * Returns {@code true} when the operation has an event stream on either
     * its input or output.
     */
    public static boolean hasEventStream(Model model, OperationShape op) {
        EventStreamIndex idx = EventStreamIndex.of(model);
        return idx.getInputInfo(op).isPresent() || idx.getOutputInfo(op).isPresent();
    }
}
