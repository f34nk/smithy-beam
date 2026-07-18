package io.smithy.beam.elixir;

import software.amazon.smithy.utils.CodeSection;

/**
 * Named regions inside {@link ElixirWriter} output. Integrations attach {@link
 * software.amazon.smithy.utils.CodeInterceptor}s to these sections.
 */
public final class ElixirWriterSections {

  private ElixirWriterSections() {}

  /** Module-level generated documentation ({@code @moduledoc}, file header comments). */
  public static final class GeneratedDocumentation implements CodeSection {}

  /** Body of a single generated operation function or callback stub. */
  public static final class OperationBody implements CodeSection {}
}
