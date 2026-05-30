package io.smithy.beam.elixir;

import io.smithy.beam.core.BeamMemberNullability;
import io.smithy.beam.core.BeamCodegenKind;
import io.smithy.beam.core.BeamDocumentation;
import io.smithy.beam.core.BeamDocumentation.DocTarget;
import io.smithy.beam.core.BeamElixirLayout;
import io.smithy.beam.core.BeamHttpBindings;
import io.smithy.beam.core.BeamProtocolCodegen;
import io.smithy.beam.core.BeamSettings;
import software.amazon.smithy.codegen.core.Symbol;
import software.amazon.smithy.codegen.core.SymbolProvider;
import software.amazon.smithy.codegen.core.WriterDelegator;
import software.amazon.smithy.codegen.core.directed.*;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.knowledge.NullableIndex;
import software.amazon.smithy.model.neighbor.Walker;
import software.amazon.smithy.model.shapes.*;
import software.amazon.smithy.model.traits.ErrorTrait;
import software.amazon.smithy.model.traits.RetryableTrait;
import software.amazon.smithy.model.traits.SparseTrait;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

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
 * generateService is a stub reserved for client/server generation.
 * Resource helpers are emitted by client/server DirectedCodegen classes.
 *
 * <p>Constraint traits do not narrow generated types; see {@link io.smithy.beam.core.BeamConstraintPolicy}.
 */
final class ElixirDirectedCodegen
        implements DirectedCodegen<ElixirContext, BeamSettings, ElixirIntegration> {

    // ── Factory methods ──────────────────────────────────────────────────────

    @Override
    public SymbolProvider createSymbolProvider(
            CreateSymbolProviderDirective<BeamSettings> directive) {
        String ns = directive.service().getId().getNamespace();
        BeamSettings settings = directive.settings();
        BeamElixirLayout layout = new BeamElixirLayout(settings, ns);
        String definitionFile = layout.typesModuleFile();
        String moduleName = ElixirSymbolProvider.toModuleName(layout.typesModuleName());
        return SymbolProvider.cache(
                new ElixirSymbolProvider(
                        settings,
                        directive.model(),
                        directive.service(),
                        definitionFile,
                        moduleName,
                        BeamCodegenKind.TYPES));
    }

    @Override
    public ElixirContext createContext(
            CreateContextDirective<BeamSettings, ElixirIntegration> directive) {
        ServiceShape service = directive.service();
        BeamHttpBindings httpBindings = BeamHttpBindings.from(directive.model());
        BeamProtocolCodegen protocolCodegen = null;
        String ns = service.getId().getNamespace();
        BeamSettings settings = directive.settings();
        BeamElixirLayout layout = new BeamElixirLayout(settings, ns);
        String definitionFile = layout.typesModuleFile();
        String moduleName = ElixirSymbolProvider.toModuleName(layout.typesModuleName());
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
                service,
                httpBindings,
                protocolCodegen,
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
        Set<ShapeId> preambleAliasesEmitted = new LinkedHashSet<>();

        ctx.writerDelegator().useFileWriter(ctx.definitionFile(), writer -> {
            writer.write("defmodule $L do", ctx.moduleName());
            writer.indent();

            writer.pushGeneratedDocumentationSection();
            BeamDocumentation.forShape(directive.service()).ifPresentOrElse(
                    doc -> BeamDocumentation.writeElixirModuledoc(writer, doc),
                    () -> {
                        BeamElixirLayout layout =
                                new BeamElixirLayout(ctx.settings(), ctx.service().getId().getNamespace());
                        String modelName = ElixirSymbolProvider.toModuleName(layout.modulePrefix());
                        writer.openBlock("@moduledoc \"\"\"");
                        writer.write("Type definitions for the $L model.", modelName);
                        writer.write("");
                        writer.write("Named after the model namespace per the baseline spec.");
                        writer.closeBlock("\"\"\"");
                    });
            writer.popState();

            writeScalarAliases(writer, model, closure, sp, preambleAliasesEmitted);
            writeListAliases(writer, model, closure, sp, ctx, preambleAliasesEmitted);
            writeMapAliases(writer, model, closure, sp, ctx, preambleAliasesEmitted);

            assertPreambleAliasCoverage(closure, preambleAliasesEmitted);
        });
    }

    static boolean isPreludeShape(Shape shape) {
        return shape.getId().getNamespace().equals("smithy.api");
    }

    /**
     * Returns true when a closure shape receives its {@code @type} alias from the preamble pass
     * rather than a {@code generate*} callback (enums, unions, and structures are excluded).
     */
    static boolean receivesPreambleTypeAlias(Shape shape) {
        if (shape instanceof EnumShape || shape instanceof IntEnumShape) {
            return false;
        }
        return shape instanceof BlobShape
                || shape instanceof BooleanShape
                || shape instanceof StringShape
                || shape instanceof ByteShape
                || shape instanceof ShortShape
                || shape instanceof IntegerShape
                || shape instanceof LongShape
                || shape instanceof FloatShape
                || shape instanceof DoubleShape
                || shape instanceof BigIntegerShape
                || shape instanceof BigDecimalShape
                || shape instanceof TimestampShape
                || shape instanceof DocumentShape
                || shape instanceof ListShape
                || shape instanceof MapShape;
    }

    static boolean shouldEmitPreambleTypeAlias(Shape shape) {
        return receivesPreambleTypeAlias(shape) && !isPreludeShape(shape);
    }

    /**
     * Shape ids that must receive exactly one preamble {@code @type} alias for the given closure.
     */
    static Set<ShapeId> expectedPreambleAliasShapeIds(Set<Shape> closure) {
        return closure.stream()
                .filter(ElixirDirectedCodegen::shouldEmitPreambleTypeAlias)
                .map(Shape::getId)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private static void recordPreambleAlias(Shape shape, Set<ShapeId> emitted) {
        if (!emitted.add(shape.getId())) {
            assert false : "duplicate preamble alias for " + shape.getId();
        }
    }

    private static void assertPreambleAliasCoverage(Set<Shape> closure, Set<ShapeId> emitted) {
        Set<ShapeId> expected = expectedPreambleAliasShapeIds(closure);
        for (ShapeId id : expected) {
            assert emitted.contains(id) : "missing preamble alias for " + id;
        }
        for (ShapeId id : emitted) {
            assert expected.contains(id) : "unexpected preamble alias for " + id;
        }
    }

    private void writeScalarAliases(
            ElixirWriter writer,
            Model model,
            Set<Shape> closure,
            SymbolProvider sp,
            Set<ShapeId> preambleAliasesEmitted) {
        writeElixirTypeAliases(
                writer, model.getBlobShapes(), closure, sp, preambleAliasesEmitted);
        writeElixirTypeAliases(
                writer, model.getBooleanShapes(), closure, sp, preambleAliasesEmitted);
        writeElixirTypeAliases(
                writer, model.getStringShapes(), closure, sp, preambleAliasesEmitted);
        writeElixirTypeAliases(
                writer, model.getByteShapes(), closure, sp, preambleAliasesEmitted);
        writeElixirTypeAliases(
                writer, model.getShortShapes(), closure, sp, preambleAliasesEmitted);
        writeElixirTypeAliases(
                writer, model.getIntegerShapes(), closure, sp, preambleAliasesEmitted);
        writeElixirTypeAliases(
                writer, model.getLongShapes(), closure, sp, preambleAliasesEmitted);
        writeElixirTypeAliases(
                writer, model.getFloatShapes(), closure, sp, preambleAliasesEmitted);
        writeElixirTypeAliases(
                writer, model.getDoubleShapes(), closure, sp, preambleAliasesEmitted);
        writeElixirTypeAliases(
                writer, model.getBigIntegerShapes(), closure, sp, preambleAliasesEmitted);
        writeElixirTypeAliases(
                writer, model.getBigDecimalShapes(), closure, sp, preambleAliasesEmitted);
        writeElixirTypeAliases(
                writer, model.getTimestampShapes(), closure, sp, preambleAliasesEmitted);
        writeElixirTypeAliases(
                writer, model.getDocumentShapes(), closure, sp, preambleAliasesEmitted);
    }

    private <S extends Shape> void writeElixirTypeAliases(
            ElixirWriter writer,
            Set<S> shapes,
            Set<Shape> closure,
            SymbolProvider sp,
            Set<ShapeId> preambleAliasesEmitted) {
        shapes.stream()
                .filter(closure::contains)
                .filter(ElixirDirectedCodegen::shouldEmitPreambleTypeAlias)
                .sorted(Comparator.comparing(s -> s.getId().getName()))
                .forEach(s -> {
                    recordPreambleAlias(s, preambleAliasesEmitted);
                    Symbol sym = sp.toSymbol(s);
                    String baseType = sym.getProperty("baseType", String.class).orElse("any()");
                    if (s instanceof BlobShape
                            && sym.getProperty("streamingBlob", Boolean.class).orElse(false)) {
                        writer.write(
                                "# Streaming payload; framing deferred to protocol layer.");
                    }
                    BeamDocumentation.forShape(s).ifPresent(doc -> {
                        writer.pushGeneratedDocumentationSection();
                        writer.write("# $L", s.getId().getName());
                        for (String line : doc.split("\n", -1)) {
                            if (line.isEmpty()) {
                                writer.write("#");
                            } else {
                                writer.write("# $L", line);
                            }
                        }
                        writer.popState();
                    });
                    writer.write("@type $L :: $L", sym.getName(), baseType);
                });
    }

    private void writeListAliases(
            ElixirWriter writer,
            Model model,
            Set<Shape> closure,
            SymbolProvider sp,
            ElixirContext ctx,
            Set<ShapeId> preambleAliasesEmitted) {
        model.getListShapes().stream()
                .filter(closure::contains)
                .sorted(Comparator.comparing(s -> s.getId().getName()))
                .forEach(s -> {
                    recordPreambleAlias(s, preambleAliasesEmitted);
                    Symbol sym = sp.toSymbol(s);
                    Symbol memberSym = sp.toSymbol(s.getMember());
                    String memberType = renderElixirType(ctx, memberSym);
                    if (s.hasTrait(SparseTrait.ID)) {
                        memberType = memberType + " | nil";
                    }
                    BeamDocumentation.forShape(s).ifPresent(doc -> {
                        writer.pushGeneratedDocumentationSection();
                        writer.write("# $L", s.getId().getName());
                        for (String line : doc.split("\n", -1)) {
                            if (line.isEmpty()) {
                                writer.write("#");
                            } else {
                                writer.write("# $L", line);
                            }
                        }
                        writer.popState();
                    });
                    writer.write("@type $L :: [$L]", sym.getName(), memberType);
                });
    }

    private void writeMapAliases(
            ElixirWriter writer,
            Model model,
            Set<Shape> closure,
            SymbolProvider sp,
            ElixirContext ctx,
            Set<ShapeId> preambleAliasesEmitted) {
        model.getMapShapes().stream()
                .filter(closure::contains)
                .sorted(Comparator.comparing(s -> s.getId().getName()))
                .forEach(s -> {
                    recordPreambleAlias(s, preambleAliasesEmitted);
                    Symbol sym = sp.toSymbol(s);
                    Symbol keySym = sp.toSymbol(s.getKey());
                    Symbol valueSym = sp.toSymbol(s.getValue());
                    String keyType = renderElixirType(ctx, keySym);
                    String valueType = renderElixirType(ctx, valueSym);
                    if (s.hasTrait(SparseTrait.ID)) {
                        valueType = valueType + " | nil";
                    }
                    BeamDocumentation.forShape(s).ifPresent(doc -> {
                        writer.pushGeneratedDocumentationSection();
                        writer.write("# $L", s.getId().getName());
                        for (String line : doc.split("\n", -1)) {
                            if (line.isEmpty()) {
                                writer.write("#");
                            } else {
                                writer.write("# $L", line);
                            }
                        }
                        writer.popState();
                    });
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
        ElixirContext ctx = directive.context();
        ctx.writerDelegator().useFileWriter(ctx.definitionFile(), writer -> {
            writer.dedent();
            writer.write("end");
        });
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
     * Types pass: resource helpers are emitted by client/server DirectedCodegen classes.
     */
    @Override
    public void generateResource(
            GenerateResourceDirective<ElixirContext, BeamSettings> directive) {
        // Client/server passes own resource emission.
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
            writer.pushGeneratedDocumentationSection();
            BeamDocumentation.writeShapeDocIfPresent(writer, shape, DocTarget.ELIXIR_MODuledoc);
            if (BeamDocumentation.forShape(shape).isEmpty()) {
                writer.write("@moduledoc \"String enum. Unknown values are represented as {:unknown, String.t()}.\"");
            }
            writer.write("");
            writer.popState();

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
            writer.pushGeneratedDocumentationSection();
            BeamDocumentation.writeShapeDocIfPresent(writer, shape, DocTarget.ELIXIR_MODuledoc);
            if (BeamDocumentation.forShape(shape).isEmpty()) {
                writer.write("@moduledoc \"Integer enum. Unknown values are represented as {:unknown, integer()}.\"");
            }
            writer.write("");
            writer.popState();

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
        UnionShape shape = directive.shape();
        ElixirContext ctx = directive.context();
        SymbolProvider sp = directive.symbolProvider();
        Symbol symbol = sp.toSymbol(shape);

        ctx.writerDelegator().useFileWriter(ctx.definitionFile(), writer -> {
            writer.pushGeneratedDocumentationSection();
            BeamDocumentation.writeShapeDocIfPresent(writer, shape, DocTarget.ELIXIR_TYPEDOC);
            writer.popState();

            List<String> variants = shape.members().stream()
                    .map(m -> {
                        Symbol memberSym = sp.toSymbol(m);
                        String tag = ":" + memberSym.getProperty("unionTag", String.class).orElseThrow();
                        String memberType = renderElixirType(ctx, memberSym);
                        return "{" + tag + ", " + memberType + "}";
                    })
                    .collect(Collectors.toList());
            variants.add("{:unknown, String.t()}");

            if (variants.size() <= 2) {
                writer.write("@type $L :: $L", symbol.getName(), String.join(" | ", variants));
            } else {
                writer.write("@type $L ::", symbol.getName());
                writer.indent();
                for (int i = 0; i < variants.size(); i++) {
                    String pipe = (i == 0) ? "  " : "| ";
                    writer.write("$L$L", pipe, variants.get(i));
                }
                writer.dedent();
            }
        });
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
        StructureShape shape = directive.shape();
        ElixirContext ctx = directive.context();
        SymbolProvider sp = directive.symbolProvider();
        NullableIndex nullableIndex = NullableIndex.of(directive.model());
        Symbol symbol = sp.toSymbol(shape);

        ctx.writerDelegator().useFileWriter(ctx.definitionFile(), writer -> {
            writer.write("");
            writer.openBlock("defmodule $L do", symbol.getName());
            writer.pushGeneratedDocumentationSection();
            BeamDocumentation.elixirStructureModuledoc(shape).ifPresentOrElse(
                    doc -> BeamDocumentation.writeElixirModuledoc(writer, doc),
                    () -> writer.write("@moduledoc \"structure $L\"", shape.getId().getName()));
            writer.write("");
            writer.popState();

            writeStructureTypeAndDefstruct(
                    writer,
                    ctx,
                    sp,
                    nullableIndex,
                    shape,
                    StreamSupport.stream(shape.members().spliterator(), false).toList());

            writer.closeBlock("end");
        });
    }

    /**
     * Emits a nested {@code defexception} module for {@code @error} structures with fault kind
     * and retryable metadata in {@code @moduledoc}.
     */
    @Override
    public void generateError(
            GenerateErrorDirective<ElixirContext, BeamSettings> directive) {
        ElixirContext ctx = directive.context();
        StructureShape shape = directive.shape();
        String modName = ctx.symbolProvider().toSymbol(shape).getName();
        ErrorTrait errorTrait = shape.expectTrait(ErrorTrait.class);
        boolean isRetryable = shape.hasTrait(RetryableTrait.class);

        String typesFile = new BeamElixirLayout(ctx.settings(),
                ctx.service().getId().getNamespace()).typesModuleFile();

        ctx.writerDelegator().useFileWriter(typesFile, writer -> {
            writer.write("");
            writer.write("# Error shape: $L ($L)", shape.getId(), errorTrait.getValue());
            writer.write("defmodule $L do", modName);
            writer.indent();
            writer.pushGeneratedDocumentationSection();
            BeamDocumentation.writeShapeDocIfPresent(writer, shape, DocTarget.ELIXIR_MODuledoc);
            if (BeamDocumentation.forShape(shape).isEmpty()) {
                writer.write("@moduledoc \"Error from $L (fault: $L, retryable: $L).\"",
                        shape.getId(), errorTrait.getValue(), isRetryable);
            }
            writer.write("");
            writer.popState();
            writer.write("defexception [");
            for (MemberShape member : shape.members()) {
                writer.write("  $L: nil,", member.getMemberName());
            }
            writer.write("  __beam_error_kind: :$L", errorTrait.getValue());
            writer.write("]");
            writer.write("@impl true");
            writer.write("def message(e), do: inspect(e)");
            writer.dedent();
            writer.write("end");
        });
    }

    private void writeStructureTypeAndDefstruct(
            ElixirWriter writer,
            ElixirContext ctx,
            SymbolProvider sp,
            NullableIndex nullableIndex,
            StructureShape shape,
            List<MemberShape> members) {

        writer.openBlock("@type t :: %__MODULE__{");
        for (int i = 0; i < members.size(); i++) {
            MemberShape member = members.get(i);
            Symbol memberSym = sp.toSymbol(member);
            String fieldName = memberSym.getProperty("fieldName", String.class).orElseThrow();
            String fullType = renderElixirType(ctx, memberSym);
            boolean nullable = BeamMemberNullability.isMemberNullable(nullableIndex, shape, member);
            String typeExpr = nullable ? fullType + " | nil" : fullType;
            String comma = (i < members.size() - 1) ? "," : "";
            writer.write("$L: $L$L", fieldName, typeExpr, comma);
        }
        writer.closeBlock("}");
        writer.write("");

        String fields = members.stream()
                .map(m -> ":" + sp.toSymbol(m).getProperty("fieldName", String.class).orElseThrow())
                .collect(Collectors.joining(", "));
        writer.write("defstruct [$L]", fields);
    }
}
