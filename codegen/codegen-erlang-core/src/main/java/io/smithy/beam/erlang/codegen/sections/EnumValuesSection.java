package io.smithy.beam.erlang.codegen.sections;

import software.amazon.smithy.utils.CodeSection;
import software.amazon.smithy.model.shapes.EnumShape;

/**
 * Code section wrapping the atom constants and conversion functions generated
 * for an {@code enum} shape.
 *
 * <p>Integrations can intercept this section to add additional variant
 * mappings or emit a {@code -type} declaration alongside the generated
 * {@code from_string/1} and {@code to_string/1} helpers.
 *
 * @param enumShape the enum shape being emitted
 */
public record EnumValuesSection(EnumShape enumShape) implements CodeSection {}
