package io.smithy.beam.elixir.codegen.sections;

import software.amazon.smithy.model.shapes.ServiceShape;
import software.amazon.smithy.utils.CodeSection;

public record ServerRouteSection(ServiceShape service) implements CodeSection {}
