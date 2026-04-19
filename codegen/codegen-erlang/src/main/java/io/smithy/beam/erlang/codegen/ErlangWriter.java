package io.smithy.beam.erlang.codegen;

import java.util.ArrayList;
import java.util.List;
import software.amazon.smithy.codegen.core.Symbol;
import software.amazon.smithy.codegen.core.SymbolProvider;
import software.amazon.smithy.codegen.core.SymbolWriter;
import software.amazon.smithy.model.shapes.EnumShape;
import software.amazon.smithy.model.shapes.StructureShape;
import software.amazon.smithy.model.shapes.UnionShape;

/**
 * Smithy {@link SymbolWriter} for Erlang source files.
 *
 * <p>Registers custom formatters:
 * <ul>
 *   <li>{@code $T} — Erlang type reference (atom, builtin, or module:fun)</li>
 *   <li>{@code $M} — module name (reserved-word-escaped)</li>
 *   <li>{@code $F} — function name (reserved-word-escaped)</li>
 *   <li>{@code $A} — atom literal</li>
 *   <li>{@code $D} — doc-comment line</li>
 * </ul>
 *
 */
public final class ErlangWriter extends SymbolWriter<ErlangWriter, ErlangImportContainer> {

    private final List<String> exports = new ArrayList<>();

    public ErlangWriter(String filename) {
        super(new ErlangImportContainer());
        putFormatter('T', this::formatType);
        putFormatter('M', (s, i) -> escapeModuleName(String.valueOf(s)));
        putFormatter('F', (s, i) -> escapeFunctionName(String.valueOf(s)));
        putFormatter('A', (s, i) -> formatAtom(String.valueOf(s)));
        putFormatter('D', (s, i) -> formatDocLine(String.valueOf(s)));
    }

    public static ErlangWriter factory(String filename) {
        return new ErlangWriter(filename);
    }

    /**
     * Registers a function name to be included in {@code -export([...])}.
     * Call once per public function during generation. Flushed by
     * {@link #flushExports()}.
     */
    public void addExport(String functionName, int arity) {
        exports.add(functionName + "/" + arity);
    }

    /**
     * Emits the accumulated {@code -module(…).} and {@code -export([…]).} header.
     * Called in {@code customizeBeforeIntegrations} once all exported names are known.
     */
    public ErlangWriter flushExports() {
        return this;
    }

    /**
     * Emits the {@code -module(ModuleName).} attribute line.
     */
    public ErlangWriter writeModuleHeader(String moduleName) {
        return this;
    }

    /**
     * Emits a {@code -record(Name, {fields}).} definition for a structure shape.
     */
    public ErlangWriter writeRecord(StructureShape shape, SymbolProvider symbols) {
        return this;
    }

    /**
     * Emits a {@code -type Name() :: variant1 | variant2.} for a union shape.
     */
    public ErlangWriter writeUnionType(UnionShape shape, SymbolProvider symbols) {
        return this;
    }

    /**
     * Emits atom-variant type and value declarations for an enum shape.
     */
    public ErlangWriter writeEnumType(EnumShape shape) {
        return this;
    }

    /**
     * Emits integer-variant type declarations for an int-enum shape.
     */
    public ErlangWriter writeIntEnumType(software.amazon.smithy.model.shapes.IntEnumShape shape) {
        return this;
    }

    // -------------------------------------------------------------------------
    // Private formatter helpers
    // -------------------------------------------------------------------------

    private String formatType(Object type, String indent) {
        if (type instanceof Symbol symbol) {
            Object kind = symbol.getProperty(ErlangSymbol.PROP_KIND).orElse(null);
            if (kind == ErlangSymbol.Kind.ATOM) {
                return symbol.getName();
            } else if (kind == ErlangSymbol.Kind.BUILTIN) {
                return symbol.getName();
            } else if (kind == ErlangSymbol.Kind.MODULE_REF) {
                return symbol.getName();
            }
            String defFile = symbol.getDefinitionFile();
            if (defFile != null && defFile.endsWith(".hrl")) {
                getImportContainer().addImport("", symbol.getName(), symbol);
                return "#" + symbol.getName() + "{}";
            }
        }
        return String.valueOf(type);
    }

    private String escapeModuleName(String name) {
        return ErlangReservedWords.MODULE_NAMES.escape(name);
    }

    private String escapeFunctionName(String name) {
        return ErlangReservedWords.MEMBER_NAMES.escape(name);
    }

    private String formatAtom(String name) {
        return name;
    }

    private String formatDocLine(String text) {
        return "%% " + text;
    }
}
