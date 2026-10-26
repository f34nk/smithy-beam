package io.smithy.beam.gleam;

import software.amazon.smithy.codegen.core.ImportContainer;
import software.amazon.smithy.codegen.core.Symbol;

/**
 * Accumulates Gleam import lines for symbol-driven deps. Default types output keeps this empty until
 * import emission is implemented.
 */
final class GleamImports implements ImportContainer {

  @Override
  public void importSymbol(Symbol symbol, String alias) {
    // Reserved: map Symbol metadata to import lines when protocol deps arrive.
  }

  @Override
  public String toString() {
    return "";
  }
}
