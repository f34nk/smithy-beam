package io.smithy.beam.elixir.codegen.sections;

import software.amazon.smithy.model.shapes.OperationShape;
import software.amazon.smithy.utils.CodeSection;

public record PaginationHelperSection(OperationShape operation) implements CodeSection {}
