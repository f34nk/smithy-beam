package io.smithy.beam.erlang.codegen.sections;

import software.amazon.smithy.codegen.core.CodeSection;
import software.amazon.smithy.model.shapes.EnumShape;

public record EnumValuesSection(EnumShape enumShape) implements CodeSection {}
