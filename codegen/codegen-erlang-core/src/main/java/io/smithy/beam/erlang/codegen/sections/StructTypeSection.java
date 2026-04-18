package io.smithy.beam.erlang.codegen.sections;

import software.amazon.smithy.utils.CodeSection;
import software.amazon.smithy.model.shapes.StructureShape;

/**
 * Code section wrapping the {@code -record(…)} declaration for a structure shape.
 *
 * <p>Integrations can intercept this section to add extra fields, emit
 * {@code -type} aliases, or inject type specs alongside the record declaration.
 *
 * @param structure the structure shape being emitted as an Erlang record
 */
public record StructTypeSection(StructureShape structure) implements CodeSection {}
