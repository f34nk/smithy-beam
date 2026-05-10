package io.smithy.beam.elixir;

import software.amazon.smithy.codegen.core.ImportContainer;
import software.amazon.smithy.codegen.core.Symbol;

/**
 * No-op ImportContainer: Elixir types files do not require import statements.
 */
final class ElixirImports implements ImportContainer {

    @Override
    public void importSymbol(Symbol symbol, String alias) {
        // Elixir types reference other modules by full name -- no import needed.
    }

    @Override
    public String toString() {
        return "";
    }
}
