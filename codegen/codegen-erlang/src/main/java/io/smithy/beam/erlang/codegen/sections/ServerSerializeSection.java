package io.smithy.beam.erlang.codegen.sections;

import software.amazon.smithy.model.shapes.OperationShape;
import software.amazon.smithy.utils.CodeSection;

public record ServerSerializeSection(OperationShape operation) implements CodeSection {}
