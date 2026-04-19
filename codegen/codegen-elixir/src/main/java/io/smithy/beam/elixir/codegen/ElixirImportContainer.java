package io.smithy.beam.elixir.codegen;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import software.amazon.smithy.codegen.core.ImportContainer;
import software.amazon.smithy.codegen.core.Symbol;

/**
 * Tracks {@code alias}, {@code use}, and {@code import} statements for an Elixir module body.
 *
 * <p>{@link #writeImports} emits {@code alias} first, then {@code use}, then
 * {@code import} groups, each separated by a blank line, at the top of the module body.
 */
public final class ElixirImportContainer implements ImportContainer {

    /** Fully-qualified module names that need {@code alias} statements. */
    private final Set<String> aliases = new LinkedHashSet<>();

    /** Ordered list of {@code use} statements (order matters in Elixir). */
    private final List<String> uses = new ArrayList<>();

    /** Fully-qualified module names that need {@code import} statements. */
    private final Set<String> imports = new LinkedHashSet<>();

    @Override
    public void importSymbol(Symbol symbol, String alias) {
        String defFile = symbol.getDefinitionFile();
        if (defFile != null && defFile.endsWith(".ex")) {
            aliases.add(symbol.getName());
        }
    }

    /** Adds a {@code use ModuleName} statement (order-sensitive). */
    public void addUse(String moduleName) {
        if (!uses.contains(moduleName)) {
            uses.add(moduleName);
        }
    }

    /** Adds an {@code alias ModuleName} statement. */
    public void addAlias(String moduleName) {
        aliases.add(moduleName);
    }

    /** Adds an {@code import ModuleName} statement. */
    public void addImport(String moduleName) {
        imports.add(moduleName);
    }

    /**
     * Writes accumulated {@code alias}, {@code use}, and {@code import} lines
     * to the given writer, each group separated by a blank line.
     */
    public void writeImports(ElixirWriter writer) {
        if (!aliases.isEmpty()) {
            for (String a : aliases) {
                writer.write("alias $L", a);
            }
            writer.write("");
        }
        if (!uses.isEmpty()) {
            for (String u : uses) {
                writer.write("use $L", u);
            }
            writer.write("");
        }
        if (!imports.isEmpty()) {
            for (String i : imports) {
                writer.write("import $L", i);
            }
            writer.write("");
        }
    }

    public Set<String> getAliases() {
        return aliases;
    }

    public List<String> getUses() {
        return uses;
    }

    public Set<String> getImports() {
        return imports;
    }
}
