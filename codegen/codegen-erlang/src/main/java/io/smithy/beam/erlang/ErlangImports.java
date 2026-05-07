package io.smithy.beam.erlang;

import software.amazon.smithy.codegen.core.ImportContainer;
import software.amazon.smithy.codegen.core.Symbol;

/** No-op ImportContainer: .hrl files have no import statements. */
final class ErlangImports implements ImportContainer {

    @Override
    public void importSymbol(Symbol symbol, String alias) {
        // .hrl files reference other types by name -- no import directives needed.
    }

    @Override
    public String toString() {
        return "";
    }
}
