package io.smithy.beam.erlang.codegen.sections;

import software.amazon.smithy.codegen.core.CodeSection;

public record FileHeaderSection(String filename) implements CodeSection {}
