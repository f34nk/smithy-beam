package io.smithy.beam.erlang;

import software.amazon.smithy.utils.CodeSection;

/**
 * Named regions inside {@link ErlangWriter} output. Integrations attach {@link
 * software.amazon.smithy.utils.CodeInterceptor}s to these sections.
 */
public final class ErlangWriterSections {

  private ErlangWriterSections() {}

  /** Module-level generated documentation block (percent comments). */
  public static final class GeneratedDocumentation implements CodeSection {}

  /** Body of a single generated operation function. */
  public static final class OperationBody implements CodeSection {}
}
