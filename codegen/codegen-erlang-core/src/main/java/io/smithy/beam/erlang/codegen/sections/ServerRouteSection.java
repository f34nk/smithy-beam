package io.smithy.beam.erlang.codegen.sections;

import software.amazon.smithy.utils.CodeSection;
import software.amazon.smithy.model.shapes.ServiceShape;

/**
 * Code section for the routing table in a generated server module.
 *
 * <p>Integrations can intercept this section to emit a dispatch map or
 * pattern-matching clauses that route incoming requests to the correct
 * operation handler based on path, method, or other HTTP attributes.
 *
 * @param service the service shape whose operations are being routed
 */
public record ServerRouteSection(ServiceShape service) implements CodeSection {}
