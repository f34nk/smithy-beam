package io.smithy.beam.elixir.codegen;

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
import software.amazon.smithy.model.traits.ErrorTrait;
import software.amazon.smithy.utils.CaseUtils;

/**
 * Smithy {@link SymbolWriter} for Elixir source files.
 *
 * <p>Registers custom formatters:
 * <ul>
 *   <li>{@code $T} — Elixir type reference (atom, module, or builtin)</li>
 *   <li>{@code $M} — PascalCase module name (reserved-word-escaped)</li>
 *   <li>{@code $F} — snake_case function name (reserved-word-escaped)</li>
 *   <li>{@code $A} — atom literal (prefixed with {@code :})</li>
 *   <li>{@code $D} — {@code @doc} comment line</li>
 * </ul>
 */
public final class ElixirWriter extends SymbolWriter<ElixirWriter, ElixirImportContainer> {

    public ElixirWriter(String filename) {
        super(new ElixirImportContainer());
        putFormatter('T', this::formatType);
        putFormatter('M', (s, i) -> escapeModuleName(String.valueOf(s)));
        putFormatter('F', (s, i) -> escapeFunctionName(String.valueOf(s)));
        putFormatter('A', (s, i) -> formatAtom(String.valueOf(s)));
        putFormatter('D', (s, i) -> formatDocLine(String.valueOf(s)));
        trimTrailingSpaces();
        trimBlankLines(2);
    }

    public static ElixirWriter factory(String filename) {
        return new ElixirWriter(filename);
    }

    /**
     * Opens a {@code defmodule Name do} block, runs {@code body}, then closes with {@code end}.
     */
    public ElixirWriter writeDefModule(String name, Runnable body) {
        write("defmodule $L do", name);
        indent();
        body.run();
        dedent();
        write("end");
        write("");
        return this;
    }

    /**
     * Emits a complete {@code defmodule} for a Smithy structure shape, including
     * {@code defstruct} and {@code @type t :: %__MODULE__{…}}.
     */
    public ElixirWriter writeStructModule(StructureShape shape, SymbolProvider symbols) {
        String moduleName = shape.getId().getName();
        boolean isError = shape.hasTrait(ErrorTrait.class);

        writeDefModule(moduleName, () -> {
            List<MemberShape> members = new ArrayList<>(shape.getAllMembers().values());

            if (isError) {
                // Error shapes use defexception
                if (members.isEmpty()) {
                    write("defexception []");
                } else {
                    StringJoiner fields = new StringJoiner(", ", "[", "]");
                    for (MemberShape member : members) {
                        fields.add(":" + toFieldName(member.getMemberName()));
                    }
                    write("defexception $L", fields.toString());
                }
            } else {
                // Regular structs use defstruct
                if (members.isEmpty()) {
                    write("defstruct []");
                } else {
                    StringJoiner fields = new StringJoiner(", ", "[", "]");
                    for (MemberShape member : members) {
                        fields.add(":" + toFieldName(member.getMemberName()));
                    }
                    write("defstruct $L", fields.toString());
                }
            }

            write("");

            // Emit @type t() spec
            if (members.isEmpty()) {
                write("@type t() :: %__MODULE__{}");
            } else {
                write("@type t() :: %__MODULE__{");
                indent();
                for (int i = 0; i < members.size(); i++) {
                    MemberShape member = members.get(i);
                    String fieldName = toFieldName(member.getMemberName());
                    Symbol memberSymbol = symbols.toSymbol(member);
                    boolean last = i == members.size() - 1;
                    write("$L: $T$L", fieldName, memberSymbol, last ? "" : ",");
                }
                dedent();
                write("}");
            }
        });
        return this;
    }

    /**
     * Emits a {@code defmodule} for a union shape with a
     * {@code @type t :: {:variant1, TypeA.t()} | {:variant2, TypeB.t()}} spec.
     */
    public ElixirWriter writeUnionModule(UnionShape shape, SymbolProvider symbols) {
        String moduleName = shape.getId().getName();
        writeDefModule(moduleName, () -> {
            List<MemberShape> members = new ArrayList<>(shape.getAllMembers().values());
            if (members.isEmpty()) {
                write("@type t() :: any()");
            } else {
                StringJoiner joiner = new StringJoiner(" | ");
                for (MemberShape member : members) {
                    String variantName = toFieldName(member.getMemberName());
                    Symbol memberSymbol = symbols.toSymbol(member);
                    joiner.add("{:" + variantName + ", " + formatTypeString(memberSymbol) + "}");
                }
                write("@type t() :: $L", joiner.toString());
            }
        });
        return this;
    }

    /**
     * Emits a {@code defmodule} for an enum shape with {@code @type t}, {@code from_string/1},
     * and {@code to_string/1} helpers.
     */
    public ElixirWriter writeEnumModule(EnumShape shape) {
        String moduleName = shape.getId().getName();
        writeDefModule(moduleName, () -> {
            if (shape.getEnumValues().isEmpty()) {
                write("@type t() :: atom()");
                return;
            }

            StringJoiner typeJoiner = new StringJoiner(" | ");
            for (String enumValue : shape.getEnumValues().values()) {
                typeJoiner.add(":" + toAtomValue(enumValue));
            }
            write("@type t() :: $L", typeJoiner.toString());
            write("");

            for (java.util.Map.Entry<String, String> entry : shape.getEnumValues().entrySet()) {
                String memberName = entry.getKey();
                String enumValue = entry.getValue();
                write("def from_string($S), do: :$L", memberName, toAtomValue(enumValue));
            }
            write("");
            for (java.util.Map.Entry<String, String> entry : shape.getEnumValues().entrySet()) {
                String memberName = entry.getKey();
                String enumValue = entry.getValue();
                write("def to_string(:$L), do: $S", toAtomValue(enumValue), memberName);
            }
        });
        return this;
    }

    /**
     * Emits a {@code defmodule} for an int-enum shape with {@code @type t},
     * {@code from_integer/1}, and {@code to_integer/1} helpers.
     */
    public ElixirWriter writeIntEnumModule(IntEnumShape shape) {
        String moduleName = shape.getId().getName();
        writeDefModule(moduleName, () -> {
            if (shape.getEnumValues().isEmpty()) {
                write("@type t() :: integer()");
                return;
            }

            StringJoiner typeJoiner = new StringJoiner(" | ");
            for (Integer value : shape.getEnumValues().values()) {
                typeJoiner.add(String.valueOf(value));
            }
            write("@type t() :: $L", typeJoiner.toString());
            write("");

            for (java.util.Map.Entry<String, Integer> entry : shape.getEnumValues().entrySet()) {
                String memberName = entry.getKey();
                int value = entry.getValue();
                write("def from_integer($L), do: :$L", value,
                        CaseUtils.toSnakeCase(memberName).toLowerCase(java.util.Locale.ROOT));
            }
            write("");
            for (java.util.Map.Entry<String, Integer> entry : shape.getEnumValues().entrySet()) {
                String memberName = entry.getKey();
                int value = entry.getValue();
                write("def to_integer(:$L), do: $L",
                        CaseUtils.toSnakeCase(memberName).toLowerCase(java.util.Locale.ROOT), value);
            }
        });
        return this;
    }

    /**
     * Emits a {@code @type name :: elixirType} spec line.
     */
    public ElixirWriter writeTypeSpec(String name, String elixirType) {
        write("@type $L :: $L", name, elixirType);
        return this;
    }

    /**
     * Emits a {@code @spec name(args) :: ret} spec line.
     */
    public ElixirWriter writeSpec(String name, String args, String ret) {
        write("@spec $L($L) :: $L", name, args, ret);
        return this;
    }

    // -------------------------------------------------------------------------
    // Private formatter helpers
    // -------------------------------------------------------------------------

    private String formatType(Object type, String indent) {
        if (type instanceof Symbol symbol) {
            Object kind = symbol.getProperty(ElixirSymbol.PROP_KIND).orElse(null);
            if (kind == ElixirSymbol.Kind.ATOM
                    || kind == ElixirSymbol.Kind.BUILTIN
                    || kind == ElixirSymbol.Kind.MODULE) {
                return symbol.getName();
            }
            String defFile = symbol.getDefinitionFile();
            if (defFile != null && defFile.endsWith(".ex")) {
                getImportContainer().importSymbol(symbol, symbol.getName());
                return symbol.getName() + ".t()";
            }
        }
        return String.valueOf(type);
    }

    private String formatTypeString(Symbol symbol) {
        Object kind = symbol.getProperty(ElixirSymbol.PROP_KIND).orElse(null);
        if (kind == ElixirSymbol.Kind.ATOM
                || kind == ElixirSymbol.Kind.BUILTIN
                || kind == ElixirSymbol.Kind.MODULE) {
            return symbol.getName();
        }
        String defFile = symbol.getDefinitionFile();
        if (defFile != null && defFile.endsWith(".ex")) {
            return symbol.getName() + ".t()";
        }
        return symbol.getName();
    }

    private String escapeModuleName(String name) {
        return ElixirReservedWords.MODULE_NAMES.escape(name);
    }

    private String escapeFunctionName(String name) {
        return ElixirReservedWords.MEMBER_NAMES.escape(name);
    }

    private static String formatAtom(String name) {
        return name.startsWith(":") ? name : ":" + name;
    }

    private static String formatDocLine(String text) {
        return "@doc \"\"\"\n" + text + "\n\"\"\"";
    }

    private static String toFieldName(String memberName) {
        return ElixirReservedWords.MEMBER_NAMES.escape(
                CaseUtils.toSnakeCase(memberName));
    }

    private static String toAtomValue(String enumValue) {
        return CaseUtils.toSnakeCase(enumValue).toLowerCase(java.util.Locale.ROOT);
    }
}
