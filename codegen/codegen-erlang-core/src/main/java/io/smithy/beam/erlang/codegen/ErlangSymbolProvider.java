package io.smithy.beam.erlang.codegen;

import io.smithy.beam.core.Mode;
import software.amazon.smithy.codegen.core.ReservedWords;
import software.amazon.smithy.codegen.core.Symbol;
import software.amazon.smithy.codegen.core.SymbolProvider;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.shapes.BigDecimalShape;
import software.amazon.smithy.model.shapes.BigIntegerShape;
import software.amazon.smithy.model.shapes.BlobShape;
import software.amazon.smithy.model.shapes.BooleanShape;
import software.amazon.smithy.model.shapes.ByteShape;
import software.amazon.smithy.model.shapes.DocumentShape;
import software.amazon.smithy.model.shapes.DoubleShape;
import software.amazon.smithy.model.shapes.EnumShape;
import software.amazon.smithy.model.shapes.FloatShape;
import software.amazon.smithy.model.shapes.IntEnumShape;
import software.amazon.smithy.model.shapes.IntegerShape;
import software.amazon.smithy.model.shapes.ListShape;
import software.amazon.smithy.model.shapes.LongShape;
import software.amazon.smithy.model.shapes.MapShape;
import software.amazon.smithy.model.shapes.MemberShape;
import software.amazon.smithy.model.shapes.OperationShape;
import software.amazon.smithy.model.shapes.ResourceShape;
import software.amazon.smithy.model.shapes.ServiceShape;
import software.amazon.smithy.model.shapes.Shape;
import software.amazon.smithy.model.shapes.ShapeVisitor;
import software.amazon.smithy.model.shapes.ShortShape;
import software.amazon.smithy.model.shapes.StringShape;
import software.amazon.smithy.model.shapes.StructureShape;
import software.amazon.smithy.model.shapes.TimestampShape;
import software.amazon.smithy.model.shapes.UnionShape;
import software.amazon.smithy.model.traits.ErrorTrait;
import software.amazon.smithy.model.traits.StreamingTrait;
import software.amazon.smithy.utils.CaseUtils;

/**
 * Maps Smithy shapes to Erlang-specific {@link Symbol} instances.
 *
 * <p>Implements both {@link SymbolProvider} and {@link ShapeVisitor}{@code <Symbol>}
 * so that {@link #toSymbol(Shape)} is a simple delegation to
 * {@link Shape#accept(ShapeVisitor)}.
 *
 * <h2>Naming conventions</h2>
 * <ul>
 *   <li>Shape local names are converted to {@code snake_case} via
 *       {@link CaseUtils#toSnakeCase(String)} before reserved-word escaping.
 *       This conversion happens first so that {@code GetWeather} becomes
 *       {@code get_weather} before the escaper checks for Erlang keywords.</li>
 *   <li>Module/type names use {@link ErlangReservedWords#MODULE_NAMES}.</li>
 *   <li>Record-field names use {@link ErlangReservedWords#MEMBER_NAMES}.</li>
 * </ul>
 *
 * <h2>File routing</h2>
 * <ul>
 *   <li>Service and operation shapes → {@code <outputDir>/<module>_client.erl}
 *       or {@code <module>_server.erl} depending on {@link Mode}.</li>
 *   <li>Structure, union, enum, and intEnum shapes → {@code <outputDir>/<module>_types.hrl}.</li>
 *   <li>Scalar shapes → no {@code definitionFile} (inline type expressions).</li>
 * </ul>
 *
 * <p>All returned symbols carry {@code putProperty("smithy.shape", shape)} so
 * that writers can recover the originating shape without querying the model.
 *
 * <p>Wrap instances via {@link SymbolProvider#caching(SymbolProvider)} in the
 * plugin — the {@code CodegenDirector} also caches, but an explicit wrapping
 * is recommended.
 */
public final class ErlangSymbolProvider extends ShapeVisitor.Default<Symbol> implements SymbolProvider {

    /** Symbol property key carrying the originating Smithy shape. */
    public static final String PROPERTY_SHAPE = "smithy.shape";

    private final Model model;
    private final ErlangSettings settings;
    private final Mode mode;
    private final ReservedWords moduleReserved;
    private final ReservedWords memberReserved;

    /**
     * Creates a new symbol provider for the given model, settings, and codegen mode.
     *
     * @param model    the Smithy model being code-generated
     * @param settings the resolved Erlang settings for this invocation
     * @param mode     {@link Mode#CLIENT} or {@link Mode#SERVER}
     */
    public ErlangSymbolProvider(Model model, ErlangSettings settings, Mode mode) {
        this.model = model;
        this.settings = settings;
        this.mode = mode;
        this.moduleReserved = ErlangReservedWords.MODULE_NAMES;
        this.memberReserved = ErlangReservedWords.MEMBER_NAMES;
    }

    // -------------------------------------------------------------------------
    // SymbolProvider
    // -------------------------------------------------------------------------

    @Override
    public Symbol toSymbol(Shape shape) {
        return shape.accept(this);
    }

    /**
     * Converts a member name to {@code snake_case} and escapes Erlang reserved
     * words by appending a trailing underscore (e.g. {@code receive} →
     * {@code receive_}).
     *
     * @param member the member shape whose name should be converted
     * @return the escaped snake_case member name
     */
    @Override
    public String toMemberName(MemberShape member) {
        String snaked = CaseUtils.toSnakeCase(member.getMemberName());
        return memberReserved.escape(snaked);
    }

    // -------------------------------------------------------------------------
    // ShapeVisitor — aggregate / service shapes
    // -------------------------------------------------------------------------

    @Override
    public Symbol serviceShape(ServiceShape shape) {
        String suffix = mode == Mode.CLIENT ? "_client" : "_server";
        String moduleName = moduleReserved.escape(settings.getModule() + suffix);
        String filename = outputDir() + "/" + moduleName + ".erl";
        return Symbol.builder()
                .name(moduleName)
                .definitionFile(filename)
                .putProperty(PROPERTY_SHAPE, shape)
                .build();
    }

    @Override
    public Symbol operationShape(OperationShape shape) {
        // Operations are rendered inside the service module file.
        String suffix = mode == Mode.CLIENT ? "_client" : "_server";
        String moduleName = moduleReserved.escape(settings.getModule() + suffix);
        String filename = outputDir() + "/" + moduleName + ".erl";
        return Symbol.builder()
                .name(moduleName)
                .definitionFile(filename)
                .putProperty(PROPERTY_SHAPE, shape)
                .build();
    }

    @Override
    public Symbol structureShape(StructureShape shape) {
        String name = toTypeName(shape.getId().getName());
        String filename = typesFile();
        Symbol.Builder builder = Symbol.builder()
                .name(name)
                .definitionFile(filename)
                .putProperty(PROPERTY_SHAPE, shape);
        if (shape.hasTrait(ErrorTrait.class)) {
            builder.putProperty("erlang.isError", true);
        }
        return builder.build();
    }

    @Override
    public Symbol unionShape(UnionShape shape) {
        String name = toTypeName(shape.getId().getName());
        return Symbol.builder()
                .name(name)
                .definitionFile(typesFile())
                .putProperty(PROPERTY_SHAPE, shape)
                .build();
    }

    @Override
    public Symbol enumShape(EnumShape shape) {
        String name = toTypeName(shape.getId().getName());
        return Symbol.builder()
                .name(name)
                .definitionFile(typesFile())
                .putProperty(PROPERTY_SHAPE, shape)
                .build();
    }

    @Override
    public Symbol intEnumShape(IntEnumShape shape) {
        String name = toTypeName(shape.getId().getName());
        return Symbol.builder()
                .name(name)
                .definitionFile(typesFile())
                .putProperty(PROPERTY_SHAPE, shape)
                .build();
    }

    @Override
    public Symbol resourceShape(ResourceShape shape) {
        return Symbol.builder()
                .name(toTypeName(shape.getId().getName()))
                .putProperty(PROPERTY_SHAPE, shape)
                .build();
    }

    // -------------------------------------------------------------------------
    // ShapeVisitor — member shapes
    // -------------------------------------------------------------------------

    @Override
    public Symbol memberShape(MemberShape shape) {
        return toSymbol(model.expectShape(shape.getTarget()));
    }

    // -------------------------------------------------------------------------
    // ShapeVisitor — scalar shapes
    // -------------------------------------------------------------------------

    @Override
    public Symbol stringShape(StringShape shape) {
        return ErlangSymbol.builtin("binary()");
    }

    @Override
    public Symbol booleanShape(BooleanShape shape) {
        return ErlangSymbol.builtin("boolean()");
    }

    @Override
    public Symbol byteShape(ByteShape shape) {
        return ErlangSymbol.builtin("integer()");
    }

    @Override
    public Symbol shortShape(ShortShape shape) {
        return ErlangSymbol.builtin("integer()");
    }

    @Override
    public Symbol integerShape(IntegerShape shape) {
        return ErlangSymbol.builtin("integer()");
    }

    @Override
    public Symbol longShape(LongShape shape) {
        return ErlangSymbol.builtin("integer()");
    }

    @Override
    public Symbol floatShape(FloatShape shape) {
        return ErlangSymbol.builtin("float()");
    }

    @Override
    public Symbol doubleShape(DoubleShape shape) {
        return ErlangSymbol.builtin("float()");
    }

    @Override
    public Symbol bigIntegerShape(BigIntegerShape shape) {
        return ErlangSymbol.builtin("integer()");
    }

    @Override
    public Symbol bigDecimalShape(BigDecimalShape shape) {
        return ErlangSymbol.builtin("{integer(), integer()}");
    }

    @Override
    public Symbol timestampShape(TimestampShape shape) {
        return ErlangSymbol.builtin("integer()");
    }

    @Override
    public Symbol documentShape(DocumentShape shape) {
        return ErlangSymbol.builtin("term()");
    }

    @Override
    public Symbol blobShape(BlobShape shape) {
        if (shape.hasTrait(StreamingTrait.class)) {
            return ErlangSymbol.builtin("fun(() -> {ok, binary()} | done | {error, term()})");
        }
        return ErlangSymbol.builtin("binary()");
    }

    // -------------------------------------------------------------------------
    // ShapeVisitor — collection shapes
    // -------------------------------------------------------------------------

    @Override
    public Symbol listShape(ListShape shape) {
        Symbol memberSymbol = toSymbol(model.expectShape(shape.getMember().getTarget()));
        return Symbol.builder()
                .name("[T]")
                .putProperty(ErlangSymbol.PROPERTY_KIND, ErlangSymbol.KIND_BUILTIN)
                .addReference(memberSymbol)
                .build();
    }

    @Override
    public Symbol mapShape(MapShape shape) {
        Symbol valueSymbol = toSymbol(model.expectShape(shape.getValue().getTarget()));
        return Symbol.builder()
                .name("#{binary() => T}")
                .putProperty(ErlangSymbol.PROPERTY_KIND, ErlangSymbol.KIND_BUILTIN)
                .addReference(valueSymbol)
                .build();
    }

    // -------------------------------------------------------------------------
    // ShapeVisitor — default fallback
    // -------------------------------------------------------------------------

    @Override
    public Symbol getDefault(Shape shape) {
        return ErlangSymbol.builtin("term()");
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /**
     * Converts a Smithy shape local name to a snake_case Erlang type atom,
     * applying reserved-word escaping at the module level.
     *
     * <p>Example: {@code "GetWeather"} → {@code "get_weather"}.
     * The snake_case conversion always runs before the reserved-word escaper
     * so that the keyword check operates on the final lowercase form.
     */
    private String toTypeName(String localName) {
        String snaked = CaseUtils.toSnakeCase(localName);
        return moduleReserved.escape(snaked);
    }

    /** Returns the output directory, stripping any trailing slash. */
    private String outputDir() {
        String dir = settings.getOutputDir();
        return dir.endsWith("/") ? dir.substring(0, dir.length() - 1) : dir;
    }

    /** Returns the path to the types HRL file for this module. */
    private String typesFile() {
        return outputDir() + "/" + settings.getModule() + "_types.hrl";
    }
}
