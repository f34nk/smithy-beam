package io.smithy.beam.erlang.codegen;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;
import software.amazon.smithy.codegen.core.Symbol;
import software.amazon.smithy.codegen.core.SymbolProvider;
import software.amazon.smithy.codegen.core.SymbolWriter;
import software.amazon.smithy.model.shapes.MemberShape;
import software.amazon.smithy.model.shapes.Shape;
import software.amazon.smithy.model.shapes.StructureShape;

/**
 * Erlang-specific {@link SymbolWriter}.
 *
 * <p>Extends {@code SymbolWriter} with custom formatters and helper methods
 * for emitting syntactically correct Erlang source.
 *
 * <h2>Custom formatters</h2>
 * <ul>
 *   <li>{@code $T} — Erlang type expression, delegating on the {@code erlang.kind} symbol
 *       property; also registers HRL include-lib paths via the import container.</li>
 *   <li>{@code $M} — module or type name (snake_case, reserved-word escaped).</li>
 *   <li>{@code $F} — function name (snake_case, reserved-word escaped).</li>
 *   <li>{@code $A} — atom literal; wraps in single quotes when the value
 *       is not a plain lowercase identifier.</li>
 *   <li>{@code $D} — {@code %%} doc-comment line, wrapping long text at 80 chars.</li>
 * </ul>
 */
public final class ErlangWriter extends SymbolWriter<ErlangWriter, ErlangImportContainer> {

    private static final Pattern BARE_ATOM = Pattern.compile("^[a-z][a-z0-9_@]*$");
    private static final int DOC_WRAP_WIDTH = 80;

    private final String filename;
    private final List<String> exportedFunctions = new ArrayList<>();

    /**
     * Creates a new writer for the given Erlang source file.
     *
     * @param filename the name of the file being generated (used for diagnostics)
     */
    public ErlangWriter(String filename) {
        super(new ErlangImportContainer());
        this.filename = filename;

        putFormatter('T', this::formatType);
        putFormatter('M', this::formatModule);
        putFormatter('F', this::formatFunction);
        putFormatter('A', this::formatAtom);
        putFormatter('D', this::formatDoc);
    }

    /**
     * Factory method compatible with {@link software.amazon.smithy.codegen.core.SymbolWriter.Factory}.
     *
     * @param filename  the filename of the file to generate
     * @param namespace ignored for Erlang (module names come from the model)
     * @return a new {@code ErlangWriter}
     */
    public static ErlangWriter factory(String filename, String namespace) {
        return new ErlangWriter(filename);
    }

    // -------------------------------------------------------------------------
    // Custom formatters
    // -------------------------------------------------------------------------

    private String formatType(Object type, String indent) {
        if (!(type instanceof Symbol)) {
            throw new IllegalArgumentException(
                    "$T requires a Symbol, but got: " + type.getClass().getName());
        }
        Symbol symbol = (Symbol) type;

        addUseImports(symbol);

        Optional<String> kind = symbol.getProperty(ErlangSymbol.PROPERTY_KIND, String.class);

        if (kind.isPresent()) {
            switch (kind.get()) {
                case ErlangSymbol.KIND_ATOM:
                    return formatAtom(symbol.getName(), indent);
                case ErlangSymbol.KIND_BUILTIN:
                    return symbol.getName();
                case ErlangSymbol.KIND_MODULE_REF:
                    String mod = symbol.getNamespace();
                    String fun = symbol.getName();
                    return (mod != null && !mod.isEmpty()) ? mod + ":" + fun : fun;
                default:
                    break;
            }
        }

        String defFile = symbol.getDefinitionFile();
        if (defFile != null && defFile.endsWith(".hrl")) {
            return symbol.getName();
        }

        return symbol.getName();
    }

    private String formatModule(Object value, String indent) {
        String name = value.toString();
        return ErlangReservedWords.MODULE_NAMES.escape(name);
    }

    private String formatFunction(Object value, String indent) {
        String name = value.toString();
        return ErlangReservedWords.MEMBER_NAMES.escape(name);
    }

    private String formatAtom(Object value, String indent) {
        String name = value.toString();
        if (BARE_ATOM.matcher(name).matches()) {
            return name;
        }
        return "'" + name.replace("\\", "\\\\").replace("'", "\\'") + "'";
    }

    private String formatDoc(Object value, String indent) {
        String text = value.toString();
        StringBuilder sb = new StringBuilder();
        int maxLine = DOC_WRAP_WIDTH - indent.length() - 3;
        if (maxLine < 20) {
            maxLine = 20;
        }
        String[] words = text.split("\\s+");
        StringBuilder line = new StringBuilder("%% ");
        for (String word : words) {
            if (line.length() + word.length() + 1 > maxLine + 3 && line.length() > 3) {
                sb.append(line.toString().stripTrailing()).append("\n").append(indent).append("%% ");
                line = new StringBuilder("%% ");
            }
            if (line.length() > 3) {
                line.append(' ');
            }
            line.append(word);
        }
        if (line.length() > 3) {
            sb.append(line.toString().stripTrailing());
        }
        return sb.toString();
    }

    // -------------------------------------------------------------------------
    // toString — prepend include_lib directives
    // -------------------------------------------------------------------------

    @Override
    public String toString() {
        String body = normalizeWhitespace(super.toString());
        String imports = getImportContainer().toString();
        if (imports.isEmpty()) {
            return body;
        }
        return imports + "\n" + body;
    }

    /**
     * Trims trailing spaces from every line and collapses runs of more than two
     * consecutive blank lines down to two.
     */
    private static String normalizeWhitespace(String source) {
        String[] lines = source.split("\n", -1);
        StringBuilder sb = new StringBuilder();
        int blankRun = 0;
        for (String line : lines) {
            String trimmed = line.stripTrailing();
            if (trimmed.isEmpty()) {
                blankRun++;
                if (blankRun <= 2) {
                    sb.append('\n');
                }
            } else {
                blankRun = 0;
                sb.append(trimmed).append('\n');
            }
        }
        return sb.toString();
    }

    // -------------------------------------------------------------------------
    // Module-header helpers
    // -------------------------------------------------------------------------

    /**
     * Emits the Erlang module header:
     * <pre>
     * -module(moduleName).
     * -export([]).
     * </pre>
     *
     * <p>The export list written here is a placeholder. Call
     * {@link #addExport(String, int)} during generation to accumulate entries,
     * then {@link #flushExports()} in {@code customizeBeforeIntegrations} to
     * rewrite it with the full list.
     *
     * @param moduleName the Erlang module atom, e.g. {@code "weather_client"}
     */
    public ErlangWriter writeModuleHeader(String moduleName) {
        write("-module($1L).", moduleName);
        write("-export([$1L]).", buildExportList());
        return this;
    }

    /**
     * Records a function name/arity pair to include in the module's export list.
     *
     * @param name  the function name
     * @param arity the arity (number of arguments)
     */
    public ErlangWriter addExport(String name, int arity) {
        exportedFunctions.add(name + "/" + arity);
        return this;
    }

    /**
     * Returns the current export list formatted for use in {@code -export([…]).}.
     */
    private String buildExportList() {
        return String.join(", ", exportedFunctions);
    }

    // -------------------------------------------------------------------------
    // Shape-writing helpers
    // -------------------------------------------------------------------------

    /**
     * Emits an Erlang {@code -record} declaration for the given structure.
     *
     * <p>Example output:
     * <pre>
     * -record(get_weather_input, {
     *     city :: binary(),
     *     country :: binary()
     * }).
     * </pre>
     *
     * @param shape   the structure shape to emit
     * @param symbols the symbol provider used to resolve member types
     */
    public ErlangWriter writeRecord(StructureShape shape, SymbolProvider symbols) {
        Symbol structSymbol = symbols.toSymbol(shape);
        String recordName = structSymbol.getName();

        List<MemberShape> members = new ArrayList<>(shape.getAllMembers().values());
        if (members.isEmpty()) {
            write("-record($L, {}).", recordName);
            return this;
        }

        write("-record($L, {", recordName);
        indent();
        for (int i = 0; i < members.size(); i++) {
            MemberShape member = members.get(i);
            String fieldName = symbols.toMemberName(member);
            Symbol typeSymbol = symbols.toSymbol(member);
            String typeName = formatType(typeSymbol, "");
            String comma = (i < members.size() - 1) ? "," : "";
            write("$L :: $L$L", fieldName, typeName, comma);
        }
        dedent();
        write("}).");
        return this;
    }

    /**
     * Emits an Erlang {@code -type} alias declaration.
     *
     * <p>Example output:
     * <pre>
     * -type get_weather_error() :: {error, get_weather_error}.
     * </pre>
     *
     * @param shape       the shape being aliased
     * @param erlangType  the Erlang type expression on the right-hand side
     * @param symbols     the symbol provider used to derive the type name
     */
    public ErlangWriter writeTypeAlias(Shape shape, String erlangType, SymbolProvider symbols) {
        Symbol sym = symbols.toSymbol(shape);
        write("-type $L() :: $L.", sym.getName(), erlangType);
        return this;
    }

    /**
     * Returns the filename this writer was created for.
     */
    public String getFilename() {
        return filename;
    }
}
