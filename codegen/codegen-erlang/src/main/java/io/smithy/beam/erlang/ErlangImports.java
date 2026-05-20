package io.smithy.beam.erlang;

import software.amazon.smithy.codegen.core.ImportContainer;
import software.amazon.smithy.codegen.core.Symbol;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Renders Erlang header fragments before the main writer body.
 * Types-only .hrl output keeps this empty. Generated .erl modules add
 * -include or -include_lib lines here.
 */
final class ErlangImports implements ImportContainer {

    private final Set<String> includeLines = new LinkedHashSet<>();

    void addIncludeRelative(String relativePath) {
        includeLines.add("-include(\"" + relativePath + "\").");
    }

    void addIncludeLib(String application, String includePath) {
        includeLines.add("-include_lib(\"" + application + "\", \"" + includePath + "\").");
    }

    @Override
    public void importSymbol(Symbol symbol, String alias) {
        // Reserved: map Symbol metadata to include paths when protocol deps arrive.
    }

    @Override
    public String toString() {
        if (includeLines.isEmpty()) {
            return "";
        }
        return includeLines.stream().collect(Collectors.joining("\n", "", "\n"));
    }
}
