package io.smithy.beam.erlang.codegen.sections;

import software.amazon.smithy.utils.CodeSection;
import software.amazon.smithy.model.shapes.OperationShape;

/**
 * Code section for the {@code %%} doc-comment block immediately preceding
 * an operation function.
 *
 * <p>Integrations can intercept this section to emit {@code @doc} annotations,
 * parameter descriptions, or example snippets derived from the model's
 * {@code @documentation} trait.
 *
 * @param operation the operation shape being documented
 */
public record OperationDocSection(OperationShape operation) implements CodeSection {}
