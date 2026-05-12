package io.smithy.beam.erlang;

import io.smithy.beam.core.BeamCodegenKind;
import io.smithy.beam.core.BeamNameUtils;
import io.smithy.beam.core.BeamSettings;
import software.amazon.smithy.codegen.core.ReservedWords;
import software.amazon.smithy.codegen.core.ReservedWordsBuilder;
import software.amazon.smithy.codegen.core.Symbol;
import software.amazon.smithy.codegen.core.SymbolProvider;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.neighbor.Walker;
import software.amazon.smithy.model.shapes.*;

import java.util.*;
import java.util.function.Function;

@SuppressWarnings("unused")
final class ErlangSymbolProvider implements SymbolProvider, ShapeVisitor<Symbol> {

    private final BeamSettings settings;
    private final Model model;
    private final ServiceShape service;
    private final String definitionFile;
    private final BeamCodegenKind kind;
    private final ReservedWords typeNameEscaper;
    private final ReservedWords fieldNameEscaper;
    private final ReservedWords atomEscaper;
    private final ReservedWords functionNameEscaper;
    private final Map<ShapeId, String> typeNames;
    private final Map<ShapeId, String> fieldNames;
    private final Map<ShapeId, String> unionTagNames;
    private final Map<ShapeId, Map<String, String>> enumAtomNames;

    ErlangSymbolProvider(
            BeamSettings settings,
            Model model,
            ServiceShape service,
            String definitionFile,
            BeamCodegenKind kind) {
        this.settings = settings;
        this.model = model;
        this.service = service;
        this.definitionFile = definitionFile;
        this.kind = kind;
        this.typeNameEscaper = erlangReservedWords();
        this.fieldNameEscaper = erlangReservedWords();
        this.atomEscaper = erlangReservedWords();
        this.functionNameEscaper = erlangReservedWords();
        this.typeNames = buildTypeNames();
        this.fieldNames = buildStructureFieldNames();
        this.unionTagNames = buildUnionTagNames();
        this.enumAtomNames = buildEnumAtomNames();
    }

    @Override
    public Symbol toSymbol(Shape shape) {
        return shape.accept(this);
    }

    // ── Prelude scalar shapes ────────────────────────────────────────────────

    @Override
    public Symbol blobShape(BlobShape shape) {
        return isPrelude(shape) ? builtin("binary()") : namedScalar(shape, "binary()");
    }

    @Override
    public Symbol booleanShape(BooleanShape shape) {
        return isPrelude(shape) ? builtin("boolean()") : namedScalar(shape, "boolean()");
    }

    @Override
    public Symbol stringShape(StringShape shape) {
        return isPrelude(shape) ? builtin("binary()") : namedScalar(shape, "binary()");
    }

    @Override
    public Symbol byteShape(ByteShape shape) {
        return isPrelude(shape) ? builtin("integer()") : namedScalar(shape, "integer()");
    }

    @Override
    public Symbol shortShape(ShortShape shape) {
        return isPrelude(shape) ? builtin("integer()") : namedScalar(shape, "integer()");
    }

    @Override
    public Symbol integerShape(IntegerShape shape) {
        return isPrelude(shape) ? builtin("integer()") : namedScalar(shape, "integer()");
    }

    @Override
    public Symbol longShape(LongShape shape) {
        return isPrelude(shape) ? builtin("integer()") : namedScalar(shape, "integer()");
    }

    @Override
    public Symbol floatShape(FloatShape shape) {
        return isPrelude(shape) ? builtin("float()") : namedScalar(shape, "float()");
    }

    @Override
    public Symbol doubleShape(DoubleShape shape) {
        return isPrelude(shape) ? builtin("float()") : namedScalar(shape, "float()");
    }

    @Override
    public Symbol bigIntegerShape(BigIntegerShape shape) {
        return isPrelude(shape) ? builtin("integer()") : namedScalar(shape, "integer()");
    }

    @Override
    public Symbol bigDecimalShape(BigDecimalShape shape) {
        // term() because there is no standard big-decimal type in OTP; decimal:decimal() via optional lib
        return isPrelude(shape) ? builtin("term()") : namedScalar(shape, "term()");
    }

    @Override
    public Symbol timestampShape(TimestampShape shape) {
        // Type-surface representation for a Smithy instant. Wire timestamp
        // format handling belongs to future protocol serializers.
        return isPrelude(shape) ? builtin("erlang:timestamp()") : namedScalar(shape, "erlang:timestamp()");
    }

    @Override
    public Symbol documentShape(DocumentShape shape) {
        return isPrelude(shape) ? builtin("term()") : namedScalar(shape, "term()");
    }

    // ── Aggregate shapes ─────────────────────────────────────────────────────

    @Override
    public Symbol listShape(ListShape shape) {
        return namedType(shape);
    }

    @Override
    public Symbol mapShape(MapShape shape) {
        return namedType(shape);
    }

    @Override
    public Symbol unionShape(UnionShape shape) {
        return namedType(shape);
    }

    @Override
    public Symbol structureShape(StructureShape shape) {
        return namedType(shape);
    }

    @Override
    public Symbol memberShape(MemberShape shape) {
        Symbol.Builder builder = toSymbol(model.expectShape(shape.getTarget())).toBuilder();
        Optional.ofNullable(fieldNames.get(shape.getId()))
            .ifPresent(name -> builder.putProperty("fieldName", name));
        Optional.ofNullable(unionTagNames.get(shape.getId()))
            .ifPresent(name -> builder.putProperty("unionTag", name));
        return builder.build();
    }

    // ── Enum shapes ──────────────────────────────────────────────────────────

    @Override
    public Symbol enumShape(EnumShape shape) {
        return namedType(shape).toBuilder()
            .putProperty("enumAtoms", new ArrayList<>(enumAtomNames.get(shape.getId()).values()))
            .build();
    }

    @Override
    public Symbol intEnumShape(IntEnumShape shape) {
        return namedType(shape).toBuilder()
            .putProperty("enumAtoms", new ArrayList<>(enumAtomNames.get(shape.getId()).values()))
            .build();
    }

    // ── Service / Operation / Resource (not types) ───────────────────────────

    @Override
    public Symbol serviceShape(ServiceShape shape) {
        return builtin("service");
    }

    @Override
    public Symbol operationShape(OperationShape shape) {
        return builtin("operation");
    }

    @Override
    public Symbol resourceShape(ResourceShape shape) {
        return builtin("resource");
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private Symbol builtin(String name) {
        return Symbol.builder()
            .name(name)
            .putProperty("builtIn", true)
            .build();
    }

    /**
     * Named scalar alias: the symbol points to the definition file so that the
     * DirectedCodegen can write "-type basic_foo() :: underlying()." for it.
     * The "baseType" property carries the underlying Erlang type for generation.
     */
    private Symbol namedScalar(Shape shape, String baseType) {
        String name = toTypeName(shape);
        return Symbol.builder()
            .name(name + "()")
            .definitionFile(definitionFile)
            .putProperty("builtIn", false)
            .putProperty("baseType", baseType)
            .build();
    }

    private Symbol namedType(Shape shape) {
        String name = toTypeName(shape);
        return Symbol.builder()
            .name(name + "()")
            .definitionFile(definitionFile)
            .putProperty("builtIn", false)
            .build();
    }

    private boolean isPrelude(Shape shape) {
        return shape.getId().getNamespace().equals("smithy.api");
    }

    String toTypeName(Shape shape) {
        return typeNames.getOrDefault(
            shape.getId(),
            typeNameEscaper.escape(toSnakeCase(shape.getId().getName(service))));
    }

    String toFieldName(MemberShape member) {
        return fieldNames.getOrDefault(
            member.getId(),
            fieldNameEscaper.escape(toSnakeCase(member.getMemberName())));
    }

    String toUnionTagName(MemberShape member) {
        return unionTagNames.getOrDefault(
            member.getId(),
            atomEscaper.escape(toSnakeCase(member.getMemberName())));
    }

    String toFunctionName(String functionName) {
        return functionNameEscaper.escape(toSnakeCase(functionName));
    }

    List<String> toEnumAtomNames(EnumShape shape) {
        return new ArrayList<>(enumAtomNames.get(shape.getId()).values());
    }

    List<String> toEnumAtomNames(IntEnumShape shape) {
        return new ArrayList<>(enumAtomNames.get(shape.getId()).values());
    }

    private Map<ShapeId, String> buildTypeNames() {
        List<Shape> shapes = new Walker(model).walkShapes(service).stream()
            .filter(shape -> !isPrelude(shape))
            .sorted(Comparator.comparing(shape -> shape.getId().toString()))
            .toList();
        return indexShapeNames(shapes, shape ->
            typeNameEscaper.escape(toSnakeCase(shape.getId().getName(service))));
    }

    private Map<ShapeId, String> buildStructureFieldNames() {
        Map<ShapeId, String> result = new HashMap<>();
        Set<Shape> closure = new Walker(model).walkShapes(service);
        model.getStructureShapes().stream()
            .filter(closure::contains)
            .forEach(shape -> result.putAll(indexMemberNames(new ArrayList<>(shape.members()), member ->
                fieldNameEscaper.escape(toSnakeCase(member.getMemberName())))));
        return result;
    }

    private Map<ShapeId, String> buildUnionTagNames() {
        Map<ShapeId, String> result = new HashMap<>();
        Set<Shape> closure = new Walker(model).walkShapes(service);
        model.getUnionShapes().stream()
            .filter(closure::contains)
            .forEach(shape -> result.putAll(indexMemberNames(new ArrayList<>(shape.members()), member ->
                atomEscaper.escape(toSnakeCase(member.getMemberName())))));
        return result;
    }

    private Map<ShapeId, Map<String, String>> buildEnumAtomNames() {
        Map<ShapeId, Map<String, String>> result = new HashMap<>();
        Set<Shape> closure = new Walker(model).walkShapes(service);
        model.getEnumShapes().stream()
            .filter(closure::contains)
            .forEach(shape -> result.put(shape.getId(),
                BeamNameUtils.deconflict(shape.getEnumValues().keySet().stream().toList(),
                    name -> atomEscaper.escape(toSnakeCase(name)))));
        model.getIntEnumShapes().stream()
            .filter(closure::contains)
            .forEach(shape -> result.put(shape.getId(),
                BeamNameUtils.deconflict(shape.getEnumValues().keySet().stream().toList(),
                    name -> atomEscaper.escape(toSnakeCase(name)))));
        return result;
    }

    private static Map<ShapeId, String> indexShapeNames(
            List<Shape> shapes, Function<Shape, String> escapedName) {
        Map<ShapeId, String> result = new HashMap<>();
        BeamNameUtils.deconflict(shapes, escapedName)
            .forEach((shape, name) -> result.put(shape.getId(), name));
        return result;
    }

    private static Map<ShapeId, String> indexMemberNames(
            List<MemberShape> members, Function<MemberShape, String> escapedName) {
        Map<ShapeId, String> result = new HashMap<>();
        BeamNameUtils.deconflict(members, escapedName)
            .forEach((member, name) -> result.put(member.getId(), name));
        return result;
    }

    static String toSnakeCase(String name) {
        return name
            .replaceAll("([a-z])([A-Z])", "$1_$2")
            .replaceAll("([A-Z]+)([A-Z][a-z])", "$1_$2")
            .toLowerCase();
    }

    private static ReservedWords erlangReservedWords() {
        return new ReservedWordsBuilder()
            .put("after", "after_")
            .put("begin", "begin_")
            .put("case", "case_")
            .put("catch", "catch_")
            .put("end", "end_")
            .put("fun", "fun_")
            .put("if", "if_")
            .put("of", "of_")
            .put("receive", "receive_")
            .put("try", "try_")
            .put("when", "when_")
            .build();
    }
}
