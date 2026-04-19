package io.smithy.beam.erlang.codegen;

import java.util.ArrayList;
import java.util.List;
import java.util.StringJoiner;
import software.amazon.smithy.codegen.core.Symbol;
import software.amazon.smithy.codegen.core.SymbolProvider;
import software.amazon.smithy.codegen.core.SymbolWriter;
import software.amazon.smithy.model.shapes.EnumShape;
import software.amazon.smithy.model.shapes.IntEnumShape;
import software.amazon.smithy.model.shapes.MemberShape;
import software.amazon.smithy.model.shapes.StructureShape;
import software.amazon.smithy.model.shapes.UnionShape;
import software.amazon.smithy.utils.CaseUtils;

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
 * <p>The module header ({@code -module(name).} and {@code -export([...]).}) and any
 * {@code -include_lib} directives are prepended in {@link #toString()}, so callers
 * may register exports at any point during generation. Call
 * {@link #writeModuleHeader(String)} to override the module name (defaults to the
 * filename base without extension). {@link #flushExports()} is a no-op marker
 * invoked from {@code customizeBeforeIntegrations} once all exports are known.
 *
 * <p>Whitespace control: trailing spaces are trimmed per-line and more than two
 * consecutive blank lines are collapsed to two.
 */
public final class ErlangWriter extends SymbolWriter<ErlangWriter, ErlangImportContainer> {

    private String moduleName;
    private final List<String> exports = new ArrayList<>();

    public ErlangWriter(String filename) {
        super(new ErlangImportContainer());
        this.moduleName = deriveModuleName(filename);
        putFormatter('T', this::formatType);
        putFormatter('M', (s, i) -> escapeModuleName(String.valueOf(s)));
        putFormatter('F', (s, i) -> escapeFunctionName(String.valueOf(s)));
        putFormatter('A', (s, i) -> formatAtom(String.valueOf(s)));
        putFormatter('D', (s, i) -> formatDocLine(String.valueOf(s)));
        trimTrailingSpaces();
        trimBlankLines(2);
    }

    public static ErlangWriter factory(String filename) {
        return new ErlangWriter(filename);
    }

    /**
     * Overrides the module name used in the {@code -module(…).} attribute.
     * If not called, the module name is derived from the filename base
     * (e.g. {@code "src/weather_client.erl"} → {@code "weather_client"}).
     */
    public ErlangWriter writeModuleHeader(String name) {
        this.moduleName = name;
        return this;
    }

    /**
     * Marker called from {@code customizeBeforeIntegrations} once all
     * {@link #addExport(String, int)} calls have been made. The actual
     * header is assembled lazily in {@link #toString()}.
     */
    public ErlangWriter flushExports() {
        return this;
    }

    /**
     * Registers a function to be included in {@code -export([…])}.
     * Entries appear in insertion order. Flushed via {@link #toString()}.
     */
    public void addExport(String functionName, int arity) {
        exports.add(functionName + "/" + arity);
    }

    /**
     * Emits a {@code -record(Name, {fields}).} definition followed by a
     * {@code -type name() :: #name{}.} line.
     */
    public ErlangWriter writeRecord(StructureShape shape, SymbolProvider symbols) {
        String recordName = toSnakeCaseName(shape.getId().getName());
        List<MemberShape> members = new ArrayList<>(shape.getAllMembers().values());
        if (members.isEmpty()) {
            write("-record($L, {}).", recordName);
        } else {
            openBlock("-record($L, {", recordName);
            for (int i = 0; i < members.size(); i++) {
                MemberShape member = members.get(i);
                String fieldName = toMemberName(member.getMemberName());
                Symbol memberSymbol = symbols.toSymbol(member);
                boolean last = i == members.size() - 1;
                write("$L :: $T$L", fieldName, memberSymbol, last ? "" : ",");
            }
            closeBlock("}).");
        }
        write("-type $L() :: #$L{}.", recordName, recordName);
        write("");
        return this;
    }

    /**
     * Emits a {@code -type Name() :: {variant1, T1} | {variant2, T2}.} union type.
     */
    public ErlangWriter writeUnionType(UnionShape shape, SymbolProvider symbols) {
        String typeName = toSnakeCaseName(shape.getId().getName());
        List<MemberShape> members = new ArrayList<>(shape.getAllMembers().values());
        if (members.isEmpty()) {
            write("-type $L() :: term().", typeName);
            write("");
            return this;
        }
        StringJoiner joiner = new StringJoiner(" | ");
        for (MemberShape member : members) {
            String variantName = toMemberName(member.getMemberName());
            Symbol memberSymbol = symbols.toSymbol(member);
            joiner.add("{" + variantName + ", " + formatTypeString(memberSymbol) + "}");
        }
        write("-type $L() :: $L.", typeName, joiner.toString());
        write("");
        return this;
    }

    /**
     * Emits an atom-variant {@code -type name() :: atom1 | atom2.} for an enum shape.
     */
    public ErlangWriter writeEnumType(EnumShape shape) {
        String typeName = toSnakeCaseName(shape.getId().getName());
        if (shape.getEnumValues().isEmpty()) {
            write("-type $L() :: atom().", typeName);
            write("");
            return this;
        }
        StringJoiner joiner = new StringJoiner(" | ");
        for (String enumValue : shape.getEnumValues().values()) {
            joiner.add(toAtomName(enumValue));
        }
        write("-type $L() :: $L.", typeName, joiner.toString());
        write("");
        return this;
    }

    /**
     * Emits an integer-literal {@code -type name() :: 0 | 1 | 2.} for an int-enum shape.
     */
    public ErlangWriter writeIntEnumType(IntEnumShape shape) {
        String typeName = toSnakeCaseName(shape.getId().getName());
        if (shape.getEnumValues().isEmpty()) {
            write("-type $L() :: integer().", typeName);
            write("");
            return this;
        }
        StringJoiner joiner = new StringJoiner(" | ");
        for (Integer value : shape.getEnumValues().values()) {
            joiner.add(String.valueOf(value));
        }
        write("-type $L() :: $L.", typeName, joiner.toString());
        write("");
        return this;
    }

    /**
     * Prepends the {@code -module(…).}, {@code -export([…]).}, and any
     * {@code -include_lib("…").} lines before the accumulated body text.
     */
    @Override
    public String toString() {
        StringBuilder header = new StringBuilder();
        header.append("-module(").append(moduleName).append(").\n");
        if (!exports.isEmpty()) {
            header.append("-export([")
                    .append(String.join(", ", exports))
                    .append("]).\n");
        }
        header.append("\n");

        for (String lib : getImportContainer().getIncludeLibs()) {
            header.append("-include_lib(\"").append(lib).append("\").\n");
        }
        if (!getImportContainer().getIncludeLibs().isEmpty()) {
            header.append("\n");
        }

        return header + super.toString();
    }

    // -------------------------------------------------------------------------
    // Private formatter helpers
    // -------------------------------------------------------------------------

    private String formatType(Object type, String indent) {
        if (type instanceof Symbol symbol) {
            Object kind = symbol.getProperty(ErlangSymbol.PROP_KIND).orElse(null);
            if (kind == ErlangSymbol.Kind.ATOM
                    || kind == ErlangSymbol.Kind.BUILTIN
                    || kind == ErlangSymbol.Kind.MODULE_REF) {
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

    /**
     * Formats a symbol to its Erlang type string without going through the
     * {@code $T} formatter (used in string-join contexts). Also registers
     * any necessary {@code -include_lib} entries.
     */
    private String formatTypeString(Symbol symbol) {
        Object kind = symbol.getProperty(ErlangSymbol.PROP_KIND).orElse(null);
        if (kind == ErlangSymbol.Kind.ATOM
                || kind == ErlangSymbol.Kind.BUILTIN
                || kind == ErlangSymbol.Kind.MODULE_REF) {
            return symbol.getName();
        }
        String defFile = symbol.getDefinitionFile();
        if (defFile != null && defFile.endsWith(".hrl")) {
            getImportContainer().addImport("", symbol.getName(), symbol);
            return "#" + symbol.getName() + "{}";
        }
        return symbol.getName();
    }

    private String escapeModuleName(String name) {
        return ErlangReservedWords.MODULE_NAMES.escape(name);
    }

    private String escapeFunctionName(String name) {
        return ErlangReservedWords.MEMBER_NAMES.escape(name);
    }

    private static String formatAtom(String name) {
        return name;
    }

    private static String formatDocLine(String text) {
        return "%% " + text;
    }

    private static String toSnakeCaseName(String name) {
        return CaseUtils.toSnakeCase(name);
    }

    private static String toMemberName(String memberName) {
        return ErlangReservedWords.MEMBER_NAMES.escape(CaseUtils.toSnakeCase(memberName));
    }

    private static String toAtomName(String enumValue) {
        return CaseUtils.toSnakeCase(enumValue).toLowerCase(java.util.Locale.ROOT);
    }

    /**
     * Extracts the module name from a filename.
     * {@code "src/weather_client.erl"} → {@code "weather_client"}.
     */
    private static String deriveModuleName(String filename) {
        String base = filename.contains("/") ? filename.substring(filename.lastIndexOf('/') + 1) : filename;
        return base.contains(".") ? base.substring(0, base.lastIndexOf('.')) : base;
    }
}
