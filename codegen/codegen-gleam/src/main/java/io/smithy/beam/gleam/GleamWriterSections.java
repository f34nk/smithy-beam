package io.smithy.beam.gleam;

import software.amazon.smithy.utils.CodeSection;

/**
 * Named regions inside {@link GleamWriter} output. Integrations attach {@link
 * software.amazon.smithy.utils.CodeInterceptor}s to these sections.
 */
public final class GleamWriterSections {

  private GleamWriterSections() {}

  /** Module-level generated documentation (file header comments). */
  public static final class GeneratedDocumentation implements CodeSection {}

  /** Body of a single generated operation function or callback stub. */
  public static final class OperationBody implements CodeSection {}
}
