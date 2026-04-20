package io.smithy.beam.erlang.codegen.sections;

import software.amazon.smithy.model.shapes.ServiceShape;
import software.amazon.smithy.utils.CodeSection;

/**
 * Service-level injection point for the {@code errors/0}, {@code is_error/1},
 * and {@code error_to_atom/1} helper functions emitted by
 * {@code ErlangErrorIntegration} into every generated client module.
 *
 * <p>Pushed once per generated service module, after the per-operation
 * sections in {@code ErlangClientCodegen.generateService}, so all error
 * shapes referenced by any operation are already known to the codegen pass.
 */
public record ServiceErrorHelpersSection(ServiceShape service) implements CodeSection {}
