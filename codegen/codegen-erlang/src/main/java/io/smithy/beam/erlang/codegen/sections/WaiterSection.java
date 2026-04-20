package io.smithy.beam.erlang.codegen.sections;

import software.amazon.smithy.model.shapes.OperationShape;
import software.amazon.smithy.utils.CodeSection;
import software.amazon.smithy.waiters.Waiter;

public record WaiterSection(OperationShape operation, Waiter waiter) implements CodeSection {}
