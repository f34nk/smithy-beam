package io.smithy.beam.erlang.codegen.sections;

import software.amazon.smithy.utils.CodeSection;
import software.amazon.smithy.model.shapes.OperationShape;

/**
 * Code section for the body of an operation's request-send logic.
 *
 * <p>Protocol integrations intercept this section to emit the serialisation
 * and HTTP dispatch code: building the request URL, encoding the payload,
 * setting headers, and calling the HTTP client. Auth integrations may also
 * intercept here to wrap the outgoing call with signing logic.
 *
 * @param operation the operation shape whose request is being serialised
 */
public record OperationSendSection(OperationShape operation) implements CodeSection {}
