package io.smithy.beam.erlang.codegen;

import java.util.LinkedHashSet;
import java.util.Set;
import software.amazon.smithy.codegen.core.ImportContainer;
import software.amazon.smithy.codegen.core.Symbol;

/**
 * Tracks Erlang-specific imports for a single generated file.
 *
 * <p>Two kinds of cross-file references are tracked:
 * <ol>
 *   <li><strong>Header includes</strong> — {@code -include_lib("app/include/types.hrl").}
 *       directives, collected from every {@link Symbol} whose
 *       {@code definitionFile} ends with {@code .hrl} and that appears in a
 *       {@code $T} expansion within this file.</li>
 *   <li><strong>Qualified modules</strong> — the set of module names that are
 *       referenced as {@code module:fun(...)} within this file. Used by
 *       {@code ErlangWriter#formatType} to decide whether to emit a qualified
 *       call or a bare function name.</li>
 * </ol>
 *
 * <p>{@link #writeImports(ErlangWriter)} emits each {@code -include_lib("…").}
 * line followed by a blank line at the top of the file. It is called by
 * {@code ErlangWriter} before any other file content is written.
 */
public final class ErlangImportContainer implements ImportContainer {

    private final Set<String> includeLibs = new LinkedHashSet<>();
    private final Set<String> qualifiedModules = new LinkedHashSet<>();

    /**
     * Called by {@code SymbolWriter} whenever a {@link Symbol} is used via
     * {@code $T} or {@code addImport}.
     *
     * <p>If the symbol's {@code definitionFile} ends with {@code .hrl}, its
     * path is recorded as an {@code -include_lib} directive. If the symbol's
     * namespace is non-empty (indicating a {@code module:fun} reference), the
     * module name is recorded in the qualified-modules set.
     *
     * @param symbol the symbol being imported
     * @param alias  the alias under which the symbol is imported (unused in Erlang)
     */
    @Override
    public void importSymbol(Symbol symbol, String alias) {
        String definitionFile = symbol.getDefinitionFile();
        if (definitionFile != null && definitionFile.endsWith(".hrl")) {
            includeLibs.add(definitionFile);
        }

        String namespace = symbol.getNamespace();
        if (namespace != null && !namespace.isEmpty()) {
            qualifiedModules.add(namespace);
        }
    }

    /**
     * Returns whether the given module name has been referenced as a
     * qualified {@code module:fun} call in this file.
     *
     * @param moduleName the module name to test
     * @return {@code true} if the module is in the qualified-modules set
     */
    public boolean isQualifiedModule(String moduleName) {
        return qualifiedModules.contains(moduleName);
    }

    /**
     * Returns an unmodifiable view of all {@code -include_lib} paths
     * accumulated for this file.
     *
     * @return set of HRL paths to include
     */
    public Set<String> getIncludeLibs() {
        return Set.copyOf(includeLibs);
    }

    /**
     * Returns an unmodifiable view of all qualified module names accumulated
     * for this file.
     *
     * @return set of module names used in qualified calls
     */
    public Set<String> getQualifiedModules() {
        return Set.copyOf(qualifiedModules);
    }

    /**
     * Returns the accumulated includes as a formatted string.
     *
     * <p>Each line has the form {@code -include_lib("path").}. An empty
     * string is returned when there are no includes. This method is used by
     * the underlying {@code SymbolWriter} machinery; prefer
     * {@link #writeImports(ErlangWriter)} for actual code emission.
     */
    @Override
    public String toString() {
        if (includeLibs.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (String path : includeLibs) {
            sb.append("-include_lib(\"").append(path).append("\").\n");
        }
        return sb.toString();
    }
}
