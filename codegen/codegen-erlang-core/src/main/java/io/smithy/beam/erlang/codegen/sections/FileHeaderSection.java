package io.smithy.beam.erlang.codegen.sections;

import software.amazon.smithy.utils.CodeSection;

/**
 * Code section at the very top of every generated Erlang file.
 *
 * <p>Integrations can intercept this section to prepend file-level comments
 * such as a generated-code notice or a licence header.
 *
 * @param filename the name of the file being generated
 */
public record FileHeaderSection(String filename) implements CodeSection {}
