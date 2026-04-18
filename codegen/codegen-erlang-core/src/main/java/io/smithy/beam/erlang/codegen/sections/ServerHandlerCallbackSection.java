package io.smithy.beam.erlang.codegen.sections;

import software.amazon.smithy.utils.CodeSection;
import software.amazon.smithy.model.shapes.OperationShape;

/**
 * Code section for an operation's server-side handler callback stub.
 *
 * <p>Integrations can intercept this section to emit the {@code handle_request/2}
 * clause or a typed behaviour callback for a specific operation, including
 * request deserialisation, business logic delegation, and response serialisation.
 *
 * @param operation the operation shape whose server callback is being generated
 */
public record ServerHandlerCallbackSection(OperationShape operation) implements CodeSection {}
