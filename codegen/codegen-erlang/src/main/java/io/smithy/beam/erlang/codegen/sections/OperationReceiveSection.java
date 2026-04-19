package io.smithy.beam.erlang.codegen.sections;

import software.amazon.smithy.codegen.core.CodeSection;
import software.amazon.smithy.model.shapes.OperationShape;

public record OperationReceiveSection(OperationShape operation) implements CodeSection {}
