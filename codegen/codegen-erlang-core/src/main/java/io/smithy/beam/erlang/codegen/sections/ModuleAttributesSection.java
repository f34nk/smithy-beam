package io.smithy.beam.erlang.codegen.sections;

import software.amazon.smithy.utils.CodeSection;
import software.amazon.smithy.model.shapes.ServiceShape;

/**
 * Code section for Erlang module attributes that follow the {@code -module(…).} declaration.
 *
 * <p>Integrations can intercept this section to inject additional module
 * attributes such as {@code -behaviour(…).}, {@code -compile(…).} options,
 * or custom {@code -vsn(…).} entries.
 *
 * @param service the service shape being code-generated
 */
public record ModuleAttributesSection(ServiceShape service) implements CodeSection {}
