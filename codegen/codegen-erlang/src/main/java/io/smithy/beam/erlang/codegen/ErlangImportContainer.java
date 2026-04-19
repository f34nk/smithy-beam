package io.smithy.beam.erlang.codegen;

import java.util.LinkedHashSet;
import java.util.Set;
import software.amazon.smithy.codegen.core.ImportContainer;
import software.amazon.smithy.codegen.core.Symbol;

/**
 * Tracks {@code -include_lib("...")} directives and qualified module references
 * for an Erlang source file.
 *
 */
public final class ErlangImportContainer implements ImportContainer {

    private final Set<String> includeLibs = new LinkedHashSet<>();
    private final Set<String> qualifiedModules = new LinkedHashSet<>();

    @Override
    public void importSymbol(Symbol symbol, String alias) {
        String defFile = symbol.getDefinitionFile();
        if (defFile != null && defFile.endsWith(".hrl")) {
            includeLibs.add(defFile);
        } else if (defFile != null && !defFile.isEmpty()) {
            qualifiedModules.add(defFile);
        }
    }

    /**
     * Writes accumulated {@code -include_lib("...")} lines to the given writer,
     * followed by a blank line.
     */
    public void writeImports(ErlangWriter writer) {
        for (String lib : includeLibs) {
            writer.write("-include_lib(\"$L\").", lib);
        }
        if (!includeLibs.isEmpty()) {
            writer.write("");
        }
    }

    public Set<String> getIncludeLibs() {
        return includeLibs;
    }

    public Set<String> getQualifiedModules() {
        return qualifiedModules;
    }
}
