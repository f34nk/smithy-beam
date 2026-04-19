package io.smithy.beam.erlang.codegen.sections;

import software.amazon.smithy.codegen.core.CodeSection;
import software.amazon.smithy.model.shapes.ServiceShape;

public record ServerRouteSection(ServiceShape service) implements CodeSection {}
