package io.smithy.beam.elixir;

import software.amazon.smithy.codegen.core.ImportContainer;
import software.amazon.smithy.codegen.core.Symbol;

/**
 * Accumulates {@code import}, {@code alias}, and {@code require} text for symbol-driven deps.
 * Default {@code _types.ex} output keeps this empty. Do not prepend the container in {@link
 * ElixirWriter#toString()}.
 */
final class ElixirImports implements ImportContainer {

  @Override
  public void importSymbol(Symbol symbol, String alias) {
    // Reserved: map Symbol metadata to import or alias lines when protocol deps arrive.
  }

  @Override
  public String toString() {
    return "";
  }
}
