package io.smithy.beam.erlang.codegen.sections;

import software.amazon.smithy.utils.CodeSection;
import software.amazon.smithy.model.shapes.UnionShape;

/**
 * Code section wrapping the {@code -type} declaration for a union shape.
 *
 * <p>Integrations can intercept this section to add additional type variants
 * or emit companion accessor functions alongside the union type declaration.
 *
 * @param union the union shape being emitted
 */
public record UnionVariantsSection(UnionShape union) implements CodeSection {}
