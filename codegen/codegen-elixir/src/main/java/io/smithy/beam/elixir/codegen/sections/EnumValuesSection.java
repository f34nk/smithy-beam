package io.smithy.beam.elixir.codegen.sections;

import software.amazon.smithy.model.shapes.EnumShape;
import software.amazon.smithy.utils.CodeSection;

public record EnumValuesSection(EnumShape enumShape) implements CodeSection {}
