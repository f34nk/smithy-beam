package io.smithy.beam.erlang;

import software.amazon.smithy.codegen.core.ImportContainer;
import software.amazon.smithy.codegen.core.Symbol;

/**
 * Tracks Erlang header fragments for generated modules. Types-only .hrl output keeps this empty.
 * Client and server modules write -include lines in the Dependencies writer section after -module.
 */
final class ErlangImports implements ImportContainer {

  @Override
  public void importSymbol(Symbol symbol, String alias) {
    // Reserved: map Symbol metadata to include paths when protocol deps arrive.
  }

  @Override
  public String toString() {
    return "";
  }
}
