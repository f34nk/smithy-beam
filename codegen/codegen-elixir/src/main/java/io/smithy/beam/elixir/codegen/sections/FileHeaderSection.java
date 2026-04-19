package io.smithy.beam.elixir.codegen.sections;

import software.amazon.smithy.utils.CodeSection;

public record FileHeaderSection(String filename) implements CodeSection {}
