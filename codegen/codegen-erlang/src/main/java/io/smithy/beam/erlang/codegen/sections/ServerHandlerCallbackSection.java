package io.smithy.beam.erlang.codegen.sections;

import software.amazon.smithy.utils.CodeSection;
import software.amazon.smithy.model.shapes.OperationShape;

public record ServerHandlerCallbackSection(OperationShape operation) implements CodeSection {}
