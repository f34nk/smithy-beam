package io.smithy.beam.elixir;

import io.smithy.beam.core.BeamSettings;
import software.amazon.smithy.codegen.core.CodegenException;
import software.amazon.smithy.codegen.core.Symbol;
import software.amazon.smithy.codegen.core.SymbolProvider;
import software.amazon.smithy.codegen.core.WriterDelegator;
import software.amazon.smithy.codegen.core.directed.*;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.neighbor.Walker;
import software.amazon.smithy.model.shapes.EnumShape;
import software.amazon.smithy.model.shapes.IntEnumShape;
import software.amazon.smithy.model.shapes.Shape;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * DirectedCodegen implementation for the Elixir types generator.
 *
 * All types land in a single defmodule block in one .ex file.
 * The block is opened in customizeBeforeShapeGeneration and closed in
 * customizeAfterIntegrations. All generate* methods write inside the
 * open block via the shared WriterDelegator writer instance.
 *
 * CodegenDirector call order:
 * 1. customizeBeforeShapeGeneration -- open defmodule, write scalar/list/map aliases
 * 2. generate* methods (enums first, then unions, then structures)
 * 3. customizeBeforeIntegrations
 * 4. integration.customize() calls
 * 5. customizeAfterIntegrations -- close defmodule with "end"
 * 6. flushWriters
 *
 * generateService and generateResource are stubs reserved for client/server
 * generation in a future iteration. generateError fails fast because error
 * structures require exception semantics that are outside the initial
 * type-only scope.
 */
final class ElixirDirectedCodegen
        implements DirectedCodegen<ElixirContext, BeamSettings, ElixirIntegration> {

    // ── Factory methods ──────────────────────────────────────────────────────

    @Override
    public SymbolProvider createSymbolProvider(
            CreateSymbolProviderDirective<BeamSettings> directive) {
        String ns = directive.service().getId().getNamespace();
        String module = directive.settings().resolveModule(ns);
        String moduleName = ElixirSymbolProvider.toModuleName(module);
        String definitionFile = "lib/generated/" + module + "_types.ex";
        return SymbolProvider.cache(
                new ElixirSymbolProvider(
                        directive.model(), directive.service(), definitionFile, moduleName));
    }

    @Override
    public ElixirContext createContext(
            CreateContextDirective<BeamSettings, ElixirIntegration> directive) {
        String ns = directive.service().getId().getNamespace();
        String module = directive.settings().resolveModule(ns);
        String moduleName = ElixirSymbolProvider.toModuleName(module);
        String definitionFile = "lib/generated/" + module + "_types.ex";
        return new ElixirContext(
                directive.model(),
                directive.settings(),
                directive.symbolProvider(),
                directive.fileManifest(),
                new WriterDelegator<>(
                        directive.fileManifest(),
                        directive.symbolProvider(),
                        ElixirWriter.factory(moduleName)),
                directive.integrations(),
                directive.service(),
                moduleName,
                definitionFile);
    }

    // ── Customization hooks ──────────────────────────────────────────────────

    /**
     * Opens the top-level defmodule block and writes scalar, list, and map type aliases
     * for shapes in the service closure. Must run before any generate* method because
     * the module opener must be the first line of the file. The defmodule stays open;
     * customizeAfterIntegrations writes the closing end.
     */
    @Override
    public void customizeBeforeShapeGeneration(
            CustomizeDirective<ElixirContext, BeamSettings> directive) {
        ElixirContext ctx = directive.context();
        Model model = directive.model();
        SymbolProvider sp = directive.symbolProvider();
        Set<Shape> closure = new Walker(model).walkShapes(directive.service());

        ctx.writerDelegator().useFileWriter(ctx.definitionFile(), writer -> {
            writer.write("defmodule $L do", ctx.moduleName());
            writer.indent();

            writer.openBlock("@moduledoc \"\"\"");
            writer.write("Type definitions for the $L model.", ctx.moduleName());
            writer.write("");
            writer.write("Named after the model namespace per the baseline spec.");
            writer.closeBlock("\"\"\"");

            writeScalarAliases(writer, model, closure, sp);
            writeListAliases(writer, model, closure, sp, ctx);
            writeMapAliases(writer, model, closure, sp, ctx);
        });
    }

    private void writeScalarAliases(
            ElixirWriter writer, Model model, Set<Shape> closure, SymbolProvider sp) {
        writeElixirTypeAliases(writer, model.getBlobShapes(), closure, sp);
        writeElixirTypeAliases(writer, model.getBooleanShapes(), closure, sp);
        writeElixirTypeAliases(writer, model.getStringShapes(), closure, sp);
        writeElixirTypeAliases(writer, model.getByteShapes(), closure, sp);
        writeElixirTypeAliases(writer, model.getShortShapes(), closure, sp);
        writeElixirTypeAliases(writer, model.getIntegerShapes(), closure, sp);
        writeElixirTypeAliases(writer, model.getLongShapes(), closure, sp);
        writeElixirTypeAliases(writer, model.getFloatShapes(), closure, sp);
        writeElixirTypeAliases(writer, model.getDoubleShapes(), closure, sp);
        writeElixirTypeAliases(writer, model.getBigIntegerShapes(), closure, sp);
        writeElixirTypeAliases(writer, model.getBigDecimalShapes(), closure, sp);
        writeElixirTypeAliases(writer, model.getTimestampShapes(), closure, sp);
        writeElixirTypeAliases(writer, model.getDocumentShapes(), closure, sp);
    }

    private <S extends Shape> void writeElixirTypeAliases(
            ElixirWriter writer, Set<S> shapes, Set<Shape> closure, SymbolProvider sp) {
        shapes.stream()
                .filter(closure::contains)
                .sorted(Comparator.comparing(s -> s.getId().getName()))
                .forEach(s -> {
                    Symbol sym = sp.toSymbol(s);
                    String baseType = sym.getProperty("baseType", String.class).orElse("any()");
                    writer.write("@type $L :: $L", sym.getName(), baseType);
                });
    }

    private void writeListAliases(
            ElixirWriter writer, Model model, Set<Shape> closure, SymbolProvider sp, ElixirContext ctx) {
        model.getListShapes().stream()
                .filter(closure::contains)
                .sorted(Comparator.comparing(s -> s.getId().getName()))
                .forEach(s -> {
                    Symbol sym = sp.toSymbol(s);
                    Symbol memberSym = sp.toSymbol(s.getMember());
                    String memberType = renderElixirType(ctx, memberSym);
                    writer.write("@type $L :: [$L]", sym.getName(), memberType);
                });
    }

    private void writeMapAliases(
            ElixirWriter writer, Model model, Set<Shape> closure, SymbolProvider sp, ElixirContext ctx) {
        model.getMapShapes().stream()
                .filter(closure::contains)
                .sorted(Comparator.comparing(s -> s.getId().getName()))
                .forEach(s -> {
                    Symbol sym = sp.toSymbol(s);
                    Symbol keySym = sp.toSymbol(s.getKey());
                    Symbol valueSym = sp.toSymbol(s.getValue());
                    String keyType = renderElixirType(ctx, keySym);
                    String valueType = renderElixirType(ctx, valueSym);
                    writer.write("@type $L :: %{$L => $L}", sym.getName(), keyType, valueType);
                });
    }

    private String renderElixirType(ElixirContext ctx, Symbol symbol) {
        boolean builtIn = symbol.getProperty("builtIn", Boolean.class).orElse(false);
        if (builtIn) {
            return symbol.getName();
        }
        String typeKind = symbol.getProperty("typeKind", String.class).orElse("alias");
        if ("module".equals(typeKind)) {
            return ctx.moduleName() + "." + symbol.getName() + ".t()";
        }
        return ctx.moduleName() + "." + symbol.getName() + "()";
    }

    @Override
    public void customizeBeforeIntegrations(
            CustomizeDirective<ElixirContext, BeamSettings> directive) {
        // No action required for the types-only baseline.
    }

    /**
     * Closes the defmodule block opened in customizeBeforeShapeGeneration.
     * Runs after all generate* methods and all integration.customize() calls.
     */
    @Override
    public void customizeAfterIntegrations(
            CustomizeDirective<ElixirContext, BeamSettings> directive) {
        // TODO: implement in a later commit.
    }

    // ── Service / Resource / Operation stubs ─────────────────────────────────

    /**
     * Stub. Future: generate the Elixir client module
     * ({ServiceName}Client) with operation functions and HTTP plumbing.
     */
    @Override
    public void generateService(
            GenerateServiceDirective<ElixirContext, BeamSettings> directive) {
        // TODO: generate service client module.
    }

    /**
     * Stub. Future: generate resource-level helper modules.
     */
    @Override
    public void generateResource(
            GenerateResourceDirective<ElixirContext, BeamSettings> directive) {
        // TODO: generate resource modules.
    }

    // ── Type generation ──────────────────────────────────────────────────────

    /**
     * Generates a nested defmodule for a Smithy enum shape.
     *
     * Output format:
     * defmodule BasicStatus do
     * 
     * @moduledoc "String enum. Unknown values are represented as {:unknown,
     *            String.t()}."
     * @type t :: :active | :inactive | :pending | {:unknown, String.t()}
     * @spec from_string(String.t()) :: t()
     *       def from_string("ACTIVE"), do: :active
     *       ...
     *       def from_string(v), do: {:unknown, v}
     * @spec to_string(t()) :: String.t()
     *       def to_string(:active), do: "ACTIVE"
     *       ...
     *       def to_string({:unknown, v}), do: v
     * @spec values() :: [t()]
     *       def values, do: [:active, :inactive, :pending]
     *       end
     */
    @Override
    public void generateEnumShape(
            GenerateEnumDirective<ElixirContext, BeamSettings> directive) {
        EnumShape shape = directive.expectEnumShape();
        ElixirContext ctx = directive.context();
        SymbolProvider sp = directive.symbolProvider();
        Symbol symbol = sp.toSymbol(shape);
        List<String> atoms = expectStringListProperty(symbol, "enumAtoms");
        String fromFunction = symbol.expectProperty("fromValueFunction", String.class);
        String toFunction = symbol.expectProperty("toValueFunction", String.class);
        String valuesFunction = symbol.expectProperty("valuesFunction", String.class);
        List<Map.Entry<String, String>> members = new ArrayList<>(shape.getEnumValues().entrySet());

        ctx.writerDelegator().useFileWriter(ctx.definitionFile(), writer -> {
            writer.write("");
            writer.openBlock("defmodule $L do", symbol.getName());
            writer.write("@moduledoc \"String enum. Unknown values are represented as {:unknown, String.t()}.\"");
            writer.write("");

            String atomVariants = atoms.stream()
                    .map(atom -> ":" + atom)
                    .collect(Collectors.joining(" | "));
            writer.write("@type t :: $L | {:unknown, String.t()}", atomVariants);
            writer.write("");

            writer.write("@spec $L(String.t()) :: t()", fromFunction);
            for (int i = 0; i < members.size(); i++) {
                Map.Entry<String, String> entry = members.get(i);
                String atom = ":" + atoms.get(i);
                writer.write("def $L($S), do: $L", fromFunction, entry.getValue(), atom);
            }
            writer.write("def $L(v), do: {:unknown, v}", fromFunction);
            writer.write("");

            writer.write("@spec $L(t()) :: String.t()", toFunction);
            for (int i = 0; i < members.size(); i++) {
                Map.Entry<String, String> entry = members.get(i);
                String atom = ":" + atoms.get(i);
                writer.write("def $L($L), do: $S", toFunction, atom, entry.getValue());
            }
            writer.write("def $L({:unknown, v}), do: v", toFunction);
            writer.write("");

            String valuesList = atoms.stream()
                    .map(atom -> ":" + atom)
                    .collect(Collectors.joining(", "));
            writer.write("@spec values() :: [t()]");
            writer.write("def $L, do: [$L]", valuesFunction, valuesList);

            writer.closeBlock("end");
        });
    }

    /**
     * Generates a nested defmodule for a Smithy intEnum shape.
     *
     * Output format mirrors generateEnumShape but uses integer() instead of
     * String.t() and from_integer/to_integer instead of from_string/to_string.
     */
    @Override
    public void generateIntEnumShape(
            GenerateIntEnumDirective<ElixirContext, BeamSettings> directive) {
        IntEnumShape shape = directive.expectIntEnumShape();
        ElixirContext ctx = directive.context();
        SymbolProvider sp = directive.symbolProvider();
        Symbol symbol = sp.toSymbol(shape);
        List<String> atoms = expectStringListProperty(symbol, "enumAtoms");
        String fromFunction = symbol.expectProperty("fromValueFunction", String.class);
        String toFunction = symbol.expectProperty("toValueFunction", String.class);
        String valuesFunction = symbol.expectProperty("valuesFunction", String.class);
        List<Map.Entry<String, Integer>> members = new ArrayList<>(shape.getEnumValues().entrySet());

        ctx.writerDelegator().useFileWriter(ctx.definitionFile(), writer -> {
            writer.write("");
            writer.openBlock("defmodule $L do", symbol.getName());
            writer.write("@moduledoc \"Integer enum. Unknown values are represented as {:unknown, integer()}.\"");
            writer.write("");

            String atomVariants = atoms.stream()
                    .map(atom -> ":" + atom)
                    .collect(Collectors.joining(" | "));
            writer.write("@type t :: $L | {:unknown, integer()}", atomVariants);
            writer.write("");

            writer.write("@spec $L(integer()) :: t()", fromFunction);
            for (int i = 0; i < members.size(); i++) {
                Map.Entry<String, Integer> entry = members.get(i);
                String atom = ":" + atoms.get(i);
                writer.write("def $L($L), do: $L", fromFunction, entry.getValue(), atom);
            }
            writer.write("def $L(v), do: {:unknown, v}", fromFunction);
            writer.write("");

            writer.write("@spec $L(t()) :: integer()", toFunction);
            for (int i = 0; i < members.size(); i++) {
                Map.Entry<String, Integer> entry = members.get(i);
                String atom = ":" + atoms.get(i);
                writer.write("def $L($L), do: $L", toFunction, atom, entry.getValue());
            }
            writer.write("def $L({:unknown, v}), do: v", toFunction);
            writer.write("");

            String valuesList = atoms.stream()
                    .map(atom -> ":" + atom)
                    .collect(Collectors.joining(", "));
            writer.write("@spec values() :: [t()]");
            writer.write("def $L, do: [$L]", valuesFunction, valuesList);

            writer.closeBlock("end");
        });
    }

    private List<String> expectStringListProperty(Symbol symbol, String propertyName) {
        List<?> values = symbol.expectProperty(propertyName, List.class);
        return values.stream().map(String.class::cast).toList();
    }

    /**
     * Generates an @type alias for a Smithy union shape.
     *
     * Output format:
     * 
     * @type basic_union ::
     *       {:text, basic_string()}
     *       | {:number, basic_integer()}
     *       | {:flag, basic_boolean()}
     *       | {:unknown, String.t()}
     */
    @Override
    public void generateUnion(
            GenerateUnionDirective<ElixirContext, BeamSettings> directive) {
        // TODO: implement in a later commit.
    }

    /**
     * Generates a nested defmodule for a Smithy structure shape.
     *
     * Output format:
     * defmodule BasicItem do
     * 
     * @moduledoc "structure BasicItem"
     * @type t :: %__MODULE__{
     *       name: BasicTypes.basic_string(),
     *       count: BasicTypes.basic_integer() | nil
     *       }
     *       defstruct [:name, :count]
     *       end
     */
    @Override
    public void generateStructure(
            GenerateStructureDirective<ElixirContext, BeamSettings> directive) {
        // TODO: implement in a later commit.
    }

    /**
     * Error structures require exception semantics and retryable or throttling
     * metadata. The initial generator interprets types only, so it rejects
     * reachable error shapes instead of silently omitting them.
     */
    @Override
    public void generateError(
            GenerateErrorDirective<ElixirContext, BeamSettings> directive) {
        throw new CodegenException("Elixir error type generation is not implemented for "
                + directive.shape().getId()
                + ". The initial smithy-beam generator only emits type definitions.");
    }
}
