package io.smithy.beam.elixir.codegen.sections;

import software.amazon.smithy.model.shapes.UnionShape;
import software.amazon.smithy.utils.CodeSection;

public record UnionVariantsSection(UnionShape union) implements CodeSection {}
