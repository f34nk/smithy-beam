package io.smithy.beam.erlang.codegen;

import io.smithy.beam.core.Mode;
import java.util.Objects;
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
import software.amazon.smithy.utils.CaseUtils;

/**
 * The single source of truth for shape → {@link Symbol} naming decisions in the
 * Erlang codegen.
 *
 * <p>Implements {@link SymbolProvider} and dispatches each call through
 * {@link ShapeVisitor} so each shape kind has its own visitor method. The
 * mode ({@link Mode#CLIENT} vs {@link Mode#SERVER}) is captured at construction
 * time so the same provider class serves both plugins, picking
 * {@code <module>_client} or {@code <module>_server} as the service-module
 * name.
 *
 * <p>File layout produced for the symbols:
 * <ul>
 *   <li>service / operation / resource — {@code <outputDir>/<module>_{client|server}.erl}</li>
 *   <li>structure / error / union / enum / int-enum — {@code <outputDir>/<module>_{client|server}_types.hrl}</li>
 *   <li>scalars / collections — no {@code definitionFile} (in-place type expressions)</li>
 * </ul>
 *
 * <p>The types include file is mode-suffixed
 * ({@code <module>_client_types.hrl} / {@code <module>_server_types.hrl})
 * so that the client and server plugins can be configured against the same
 * Smithy model and emit into the same project without colliding on shared
 * record / type definitions. {@link ErlangWriter} derives the
 * {@code -module(…)} attribute from the filename, so the include header is
 * written as {@code -module(<module>_<mode>_types).} automatically.
 *
 * <p>Every returned symbol carries the originating {@link Shape} under the
 * {@link #PROP_SHAPE} property so that writers can recover it without having
 * to re-query the {@link Model}. Error structures additionally carry
 * {@link #PROP_IS_ERROR} = {@code true}.
 */
public final class ErlangSymbolProvider implements SymbolProvider, ShapeVisitor<Symbol> {

    /** Property key under which every returned symbol stores its originating Smithy shape. */
    public static final String PROP_SHAPE = "smithy.shape";

    /** Property key set to {@code true} for symbols representing {@code @error} structures. */
    public static final String PROP_IS_ERROR = "erlang.isError";

    private final Model model;
    private final ErlangSettings settings;
    private final Mode mode;

    private final String serviceModuleName;
    private final String serviceFile;
    private final String typesFile;

    public ErlangSymbolProvider(Model model, ErlangSettings settings, Mode mode) {
        this.model = Objects.requireNonNull(model, "model");
        this.settings = Objects.requireNonNull(settings, "settings");
        this.mode = Objects.requireNonNull(mode, "mode");

        String modeSuffix = (mode == Mode.SERVER) ? "server" : "client";
        this.serviceModuleName = settings.getModule() + "_" + modeSuffix;
        this.serviceFile = settings.getOutputDir() + "/" + serviceModuleName + ".erl";
        this.typesFile = settings.getOutputDir() + "/" + settings.getModule()
                + "_" + modeSuffix + "_types.hrl";
    }

    @Override
    public Symbol toSymbol(Shape shape) {
        Symbol base = shape.accept(this);
        // Ensure the originating shape is always recoverable from the symbol.
        if (base.getProperty(PROP_SHAPE).isPresent()) {
            return base;
        }
        return base.toBuilder().putProperty(PROP_SHAPE, shape).build();
    }

    @Override
    public String toMemberName(MemberShape shape) {
        return escapedMember(shape.getMemberName());
    }

    // -------------------------------------------------------------------------
    // Service / operation / resource — share one symbol so the writer delegator
    // routes them all into the same generated module file.
    // -------------------------------------------------------------------------

    @Override
    public Symbol serviceShape(ServiceShape shape) {
        return serviceLevelSymbol(shape);
    }

    @Override
    public Symbol operationShape(OperationShape shape) {
        return serviceLevelSymbol(shape);
    }

    @Override
    public Symbol resourceShape(ResourceShape shape) {
        return serviceLevelSymbol(shape);
    }

    private Symbol serviceLevelSymbol(Shape shape) {
        return Symbol.builder()
                .name(serviceModuleName)
                .definitionFile(serviceFile)
                .putProperty(PROP_SHAPE, shape)
                .build();
    }

    // -------------------------------------------------------------------------
    // Aggregate shapes — emitted into the mode-suffixed
    // <module>_{client|server}_types.hrl include file.
    // -------------------------------------------------------------------------

    @Override
    public Symbol structureShape(StructureShape shape) {
        Symbol.Builder builder = Symbol.builder()
                .name(escapedTypeName(shape.getId().getName()))
                .definitionFile(typesFile)
                .putProperty(PROP_SHAPE, shape);
        if (shape.hasTrait(ErrorTrait.class)) {
            builder.putProperty(PROP_IS_ERROR, Boolean.TRUE);
        }
        return builder.build();
    }

    @Override
    public Symbol unionShape(UnionShape shape) {
        return aggregateTypeSymbol(shape, shape.getId().getName());
    }

    @Override
    public Symbol enumShape(EnumShape shape) {
        return aggregateTypeSymbol(shape, shape.getId().getName());
    }

    @Override
    public Symbol intEnumShape(IntEnumShape shape) {
        return aggregateTypeSymbol(shape, shape.getId().getName());
    }

    private Symbol aggregateTypeSymbol(Shape shape, String localName) {
        return Symbol.builder()
                .name(escapedTypeName(localName))
                .definitionFile(typesFile)
                .putProperty(PROP_SHAPE, shape)
                .build();
    }

    // -------------------------------------------------------------------------
    // Scalars — built-in Erlang type expressions, no definitionFile.
    // -------------------------------------------------------------------------

    @Override
    public Symbol stringShape(StringShape shape) {
        return ErlangSymbol.builtin("binary()");
    }

    @Override
    public Symbol blobShape(BlobShape shape) {
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
    public Symbol bigIntegerShape(BigIntegerShape shape) {
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
    public Symbol bigDecimalShape(BigDecimalShape shape) {
        // Represented as a {Mantissa, Exponent} tuple; runtime helpers in
        // smithy_json own the precision conversion.
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

    // -------------------------------------------------------------------------
    // Collections — recursively materialise the inner element symbol so the
    // emitted Erlang type expression includes the resolved element type.
    // -------------------------------------------------------------------------

    @Override
    public Symbol listShape(ListShape shape) {
        Symbol elementSymbol = toSymbol(model.expectShape(shape.getMember().getTarget()));
        return Symbol.builder()
                .name("[" + symbolToTypeString(elementSymbol) + "]")
                .putProperty(ErlangSymbol.PROP_KIND, ErlangSymbol.Kind.BUILTIN)
                .addReference(elementSymbol)
                .build();
    }

    @Override
    public Symbol mapShape(MapShape shape) {
        Symbol valueSymbol = toSymbol(model.expectShape(shape.getValue().getTarget()));
        return Symbol.builder()
                .name("#{binary() => " + symbolToTypeString(valueSymbol) + "}")
                .putProperty(ErlangSymbol.PROP_KIND, ErlangSymbol.Kind.BUILTIN)
                .addReference(valueSymbol)
                .build();
    }

    // -------------------------------------------------------------------------
    // Member — resolves to the target shape's symbol; member-name escaping is
    // handled separately by toMemberName.
    // -------------------------------------------------------------------------

    @Override
    public Symbol memberShape(MemberShape shape) {
        return toSymbol(model.expectShape(shape.getTarget()));
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /** snake_case the local shape name, then apply the module/type reserved-word escaper. */
    private static String escapedTypeName(String shapeLocalName) {
        return ErlangReservedWords.MODULE_NAMES.escape(CaseUtils.toSnakeCase(shapeLocalName));
    }

    /** snake_case the member name, then apply the member reserved-word escaper. */
    private static String escapedMember(String memberName) {
        return ErlangReservedWords.MEMBER_NAMES.escape(CaseUtils.toSnakeCase(memberName));
    }

    /**
     * Renders a symbol as its inline Erlang type expression. Used when embedding
     * a referenced symbol inside a parent type string (lists, maps).
     */
    private static String symbolToTypeString(Symbol symbol) {
        Object kind = symbol.getProperty(ErlangSymbol.PROP_KIND).orElse(null);
        if (kind == ErlangSymbol.Kind.ATOM
                || kind == ErlangSymbol.Kind.BUILTIN
                || kind == ErlangSymbol.Kind.MODULE_REF) {
            return symbol.getName();
        }
        String defFile = symbol.getDefinitionFile();
        if (defFile != null && defFile.endsWith(".hrl")) {
            return "#" + symbol.getName() + "{}";
        }
        return symbol.getName();
    }

    Mode mode() {
        return mode;
    }

    ErlangSettings settings() {
        return settings;
    }
}
