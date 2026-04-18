package io.smithy.beam.erlang.codegen.sections;

import software.amazon.smithy.utils.CodeSection;
import software.amazon.smithy.model.shapes.OperationShape;

/**
 * Code section for the body of an operation's response-receive logic.
 *
 * <p>Protocol integrations intercept this section to emit the deserialisation
 * code: inspecting the HTTP status code, decoding the response payload, and
 * returning the typed output or error tuple.
 *
 * @param operation the operation shape whose response is being deserialised
 */
public record OperationReceiveSection(OperationShape operation) implements CodeSection {}
