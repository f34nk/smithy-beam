package io.smithy.beam.erlang.codegen.sections;

import software.amazon.smithy.utils.CodeSection;
import software.amazon.smithy.model.shapes.UnionShape;

public record UnionVariantsSection(UnionShape union) implements CodeSection {}
