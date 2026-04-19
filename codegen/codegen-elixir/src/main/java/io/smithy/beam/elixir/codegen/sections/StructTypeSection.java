package io.smithy.beam.elixir.codegen.sections;

import software.amazon.smithy.model.shapes.StructureShape;
import software.amazon.smithy.utils.CodeSection;

public record StructTypeSection(StructureShape structure) implements CodeSection {}
