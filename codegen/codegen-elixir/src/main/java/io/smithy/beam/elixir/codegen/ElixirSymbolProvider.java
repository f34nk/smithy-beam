package io.smithy.beam.elixir.codegen;

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
 * Elixir codegen.
 *
 * <p>Implements {@link SymbolProvider} and dispatches each call through
 * {@link ShapeVisitor} so each shape kind has its own visitor method. The
 * mode ({@link Mode#CLIENT} vs {@link Mode#SERVER}) is captured at construction
 * time so the same provider class serves both plugins, picking
 * {@code <Namespace>.Client} or {@code <Namespace>.Server} as the service-module name.
 *
 * <p>File layout produced for the symbols:
 * <ul>
 *   <li>service / operation / resource — {@code <outputDir>/<module>_{client|server}.ex}</li>
 *   <li>structure / union / enum / int-enum — {@code <outputDir>/<module>_{client|server}_types.ex}</li>
 *   <li>error structure — {@code <outputDir>/<module>_{client|server}_errors.ex}</li>
 *   <li>scalars / collections — no {@code definitionFile} (in-place type expressions)</li>
 * </ul>
 *
 * <p>Both filenames and module names for shape symbols are mode-suffixed /
 * mode-prefixed ({@code <Namespace>.{Client|Server}.Types.*} and
 * {@code .Errors.*}) so that the client and server plugins can be configured
 * against the same Smithy model and emit into the same Mix project without
 * colliding on shared shape modules.
 *
 * <p>Every returned symbol carries the originating {@link Shape} under the
 * {@link #PROP_SHAPE} property. Error structures additionally carry
 * {@link #PROP_IS_ERROR} = {@code true}.
 */
public final class ElixirSymbolProvider implements SymbolProvider, ShapeVisitor<Symbol> {

    /** Property key under which every returned symbol stores its originating Smithy shape. */
    public static final String PROP_SHAPE = "smithy.shape";

    /** Property key set to {@code true} for symbols representing {@code @error} structures. */
    public static final String PROP_IS_ERROR = "elixir.isError";

    private final Model model;
    private final ElixirSettings settings;
    private final Mode mode;

    private final String serviceModuleName;
    private final String serviceFile;
    private final String typesFile;
    private final String errorsFile;

    public ElixirSymbolProvider(Model model, ElixirSettings settings, Mode mode) {
        this.model = Objects.requireNonNull(model, "model");
        this.settings = Objects.requireNonNull(settings, "settings");
        this.mode = Objects.requireNonNull(mode, "mode");

        String modeSuffix = (mode == Mode.SERVER) ? "Server" : "Client";
        String namespace = settings.getNamespace();
        this.serviceModuleName = namespace + "." + modeSuffix;

        // Derive a snake_case filename base from the last segment of the namespace
        String modulePart = toDirName(namespace);
        String modeSuffixFile = (mode == Mode.SERVER) ? "server" : "client";
        this.serviceFile = settings.getOutputDir() + "/" + modulePart + "_" + modeSuffixFile + ".ex";
        this.typesFile = settings.getOutputDir() + "/" + modulePart + "_" + modeSuffixFile + "_types.ex";
        this.errorsFile = settings.getOutputDir() + "/" + modulePart + "_" + modeSuffixFile + "_errors.ex";
    }

    @Override
    public Symbol toSymbol(Shape shape) {
        Symbol base = shape.accept(this);
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
    // Aggregate shapes — emitted into shared type files.
    // -------------------------------------------------------------------------

    @Override
    public Symbol structureShape(StructureShape shape) {
        boolean isError = shape.hasTrait(ErrorTrait.class);
        // Mode-prefixed (e.g. "Weather.Client" / "Weather.Server") so client and
        // server plugins can coexist in a single Mix project without colliding
        // on shared shape modules.
        String moduleName = isError
                ? serviceModuleName + ".Errors." + shape.getId().getName()
                : serviceModuleName + ".Types." + shape.getId().getName();
        String defFile = isError ? errorsFile : typesFile;

        Symbol.Builder builder = Symbol.builder()
                .name(moduleName)
                .definitionFile(defFile)
                .putProperty(PROP_SHAPE, shape);
        if (isError) {
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
        String moduleName = serviceModuleName + ".Types." + localName;
        return Symbol.builder()
                .name(moduleName)
                .definitionFile(typesFile)
                .putProperty(PROP_SHAPE, shape)
                .build();
    }

    // -------------------------------------------------------------------------
    // Scalars — built-in Elixir type expressions, no definitionFile.
    // -------------------------------------------------------------------------

    @Override
    public Symbol stringShape(StringShape shape) {
        return ElixirSymbol.builtin("String.t()");
    }

    @Override
    public Symbol blobShape(BlobShape shape) {
        return ElixirSymbol.builtin("binary()");
    }

    @Override
    public Symbol booleanShape(BooleanShape shape) {
        return ElixirSymbol.builtin("boolean()");
    }

    @Override
    public Symbol byteShape(ByteShape shape) {
        return ElixirSymbol.builtin("integer()");
    }

    @Override
    public Symbol shortShape(ShortShape shape) {
        return ElixirSymbol.builtin("integer()");
    }

    @Override
    public Symbol integerShape(IntegerShape shape) {
        return ElixirSymbol.builtin("integer()");
    }

    @Override
    public Symbol longShape(LongShape shape) {
        return ElixirSymbol.builtin("integer()");
    }

    @Override
    public Symbol bigIntegerShape(BigIntegerShape shape) {
        return ElixirSymbol.builtin("integer()");
    }

    @Override
    public Symbol floatShape(FloatShape shape) {
        return ElixirSymbol.builtin("float()");
    }

    @Override
    public Symbol doubleShape(DoubleShape shape) {
        return ElixirSymbol.builtin("float()");
    }

    @Override
    public Symbol bigDecimalShape(BigDecimalShape shape) {
        return ElixirSymbol.builtin("Decimal.t()");
    }

    @Override
    public Symbol timestampShape(TimestampShape shape) {
        return ElixirSymbol.builtin("DateTime.t()");
    }

    @Override
    public Symbol documentShape(DocumentShape shape) {
        return ElixirSymbol.builtin("any()");
    }

    // -------------------------------------------------------------------------
    // Collections
    // -------------------------------------------------------------------------

    @Override
    public Symbol listShape(ListShape shape) {
        Symbol elementSymbol = toSymbol(model.expectShape(shape.getMember().getTarget()));
        return Symbol.builder()
                .name("[" + symbolToTypeString(elementSymbol) + "]")
                .putProperty(ElixirSymbol.PROP_KIND, ElixirSymbol.Kind.BUILTIN)
                .addReference(elementSymbol)
                .build();
    }

    @Override
    public Symbol mapShape(MapShape shape) {
        Symbol valueSymbol = toSymbol(model.expectShape(shape.getValue().getTarget()));
        return Symbol.builder()
                .name("%{String.t() => " + symbolToTypeString(valueSymbol) + "}")
                .putProperty(ElixirSymbol.PROP_KIND, ElixirSymbol.Kind.BUILTIN)
                .addReference(valueSymbol)
                .build();
    }

    @Override
    public Symbol memberShape(MemberShape shape) {
        return toSymbol(model.expectShape(shape.getTarget()));
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private static String escapedMember(String memberName) {
        return ElixirReservedWords.MEMBER_NAMES.escape(
                CaseUtils.toSnakeCase(memberName));
    }

    private static String symbolToTypeString(Symbol symbol) {
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

    /**
     * Converts a dot-separated PascalCase namespace (e.g. {@code "WeatherService"})
     * to a snake_case directory/file name fragment (e.g. {@code "weather_service"}).
     */
    private static String toDirName(String namespace) {
        // Use only the last segment of the namespace for the filename
        String lastSegment = namespace.contains(".")
                ? namespace.substring(namespace.lastIndexOf('.') + 1)
                : namespace;
        return CaseUtils.toSnakeCase(lastSegment).toLowerCase(java.util.Locale.ROOT);
    }

    Mode mode() {
        return mode;
    }

    ElixirSettings settings() {
        return settings;
    }
}
