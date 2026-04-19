package io.smithy.beam.erlang.codegen.sections;

import software.amazon.smithy.codegen.core.CodeSection;
import software.amazon.smithy.model.shapes.StructureShape;

public record StructTypeSection(StructureShape structure) implements CodeSection {}
