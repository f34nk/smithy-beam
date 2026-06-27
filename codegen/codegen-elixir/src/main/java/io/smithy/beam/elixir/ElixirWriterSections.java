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

  /** {@code defmodule}, {@code @behaviour}, exports, and related header lines. */
  public static final class ModuleHeader implements CodeSection {}

  /** {@code import}, {@code alias}, {@code require}, and similar dependency preamble lines. */
  public static final class Dependencies implements CodeSection {}

  /** Protocol framing hooks (serialization, metadata). */
  public static final class ProtocolHook implements CodeSection {}

  /** Transport hooks (HTTP client calls, endpoint wiring). */
  public static final class TransportHook implements CodeSection {}

  /** Body of a single generated operation function or callback stub. */
  public static final class OperationBody implements CodeSection {}
}
