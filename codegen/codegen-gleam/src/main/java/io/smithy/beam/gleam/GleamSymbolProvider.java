package io.smithy.beam.gleam;

import io.smithy.beam.core.BeamCodegenKind;
import io.smithy.beam.core.BeamGleamBuiltinTypes;
import io.smithy.beam.core.BeamGleamLayout;
import io.smithy.beam.core.BeamNameIndex;
import io.smithy.beam.core.BeamNameUtils;
import io.smithy.beam.core.BeamSettings;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import software.amazon.smithy.codegen.core.ReservedWords;
import software.amazon.smithy.codegen.core.ReservedWordsBuilder;
import software.amazon.smithy.codegen.core.Symbol;
import software.amazon.smithy.codegen.core.SymbolProvider;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.neighbor.Walker;
import software.amazon.smithy.model.shapes.*;
import software.amazon.smithy.model.traits.StreamingTrait;

final class GleamSymbolProvider implements SymbolProvider, ShapeVisitor<Symbol> {

  static final String UNKNOWN_MEMBER = "$unknown";

  private final BeamSettings settings;
  private final Model model;
  private final ServiceShape service;
  private final String definitionFile;
  private final BeamCodegenKind kind;
  private final String moduleNamespace;
  private final ReservedWords identifierEscaper;
  private final Map<ShapeId, String> typeNames;
  private final Map<ShapeId, String> fieldNames;
  private final Map<ShapeId, String> unionTagNames;
  private final Map<ShapeId, Map<String, String>> enumAtomNames;
  private final Map<ShapeId, Map<String, String>> constructorNames;
  private final Map<ShapeId, String> serviceFunctionNames;

  GleamSymbolProvider(
      BeamSettings settings,
      Model model,
      ServiceShape service,
      String definitionFile,
      String moduleNamespace,
      BeamCodegenKind kind) {
    this.settings = settings;
    this.model = model;
    this.service = service;
    this.definitionFile = definitionFile;
    this.moduleNamespace = moduleNamespace;
    this.kind = kind;
    this.identifierEscaper = gleamReservedWords();
    BeamNameIndex nameIndex =
        BeamNameIndex.of(
            model,
            service,
            BeamNameIndex.Escapers.of(
                identifierEscaper, identifierEscaper, identifierEscaper, identifierEscaper));
    this.typeNames = toPascalTypeNames(nameIndex.typeNames());
    this.fieldNames = nameIndex.fieldNames();
    this.unionTagNames = nameIndex.unionTagNames();
    this.enumAtomNames = nameIndex.enumAtomNames();
    this.serviceFunctionNames = nameIndex.serviceFunctionNames();
    this.constructorNames = buildConstructorNames();
  }

  @Override
  public Symbol toSymbol(Shape shape) {
    return shape.accept(this);
  }

  @Override
  public Symbol blobShape(BlobShape shape) {
    if (isPrelude(shape)) {
      return builtin("BitArray");
    }
    Symbol base = namedScalar(shape, "BitArray");
    if (shape.hasTrait(StreamingTrait.ID)) {
      return base.toBuilder().putProperty("streamingBlob", true).build();
    }
    return base;
  }

  @Override
  public Symbol booleanShape(BooleanShape shape) {
    return isPrelude(shape) ? builtin("Bool") : namedScalar(shape, "Bool");
  }

  @Override
  public Symbol stringShape(StringShape shape) {
    return isPrelude(shape) ? builtin("String") : namedScalar(shape, "String");
  }

  @Override
  public Symbol byteShape(ByteShape shape) {
    return isPrelude(shape) ? builtin("Int") : namedScalar(shape, "Int");
  }

  @Override
  public Symbol shortShape(ShortShape shape) {
    return isPrelude(shape) ? builtin("Int") : namedScalar(shape, "Int");
  }

  @Override
  public Symbol integerShape(IntegerShape shape) {
    return isPrelude(shape) ? builtin("Int") : namedScalar(shape, "Int");
  }

  @Override
  public Symbol longShape(LongShape shape) {
    return isPrelude(shape) ? builtin("Int") : namedScalar(shape, "Int");
  }

  @Override
  public Symbol floatShape(FloatShape shape) {
    return isPrelude(shape) ? builtin("Float") : namedScalar(shape, "Float");
  }

  @Override
  public Symbol doubleShape(DoubleShape shape) {
    return isPrelude(shape) ? builtin("Float") : namedScalar(shape, "Float");
  }

  @Override
  public Symbol bigIntegerShape(BigIntegerShape shape) {
    return isPrelude(shape) ? builtin("Int") : namedScalar(shape, "Int");
  }

  @Override
  public Symbol bigDecimalShape(BigDecimalShape shape) {
    return isPrelude(shape)
        ? builtin("bigdecimal.BigDecimal")
        : namedScalar(shape, "bigdecimal.BigDecimal");
  }

  @Override
  public Symbol timestampShape(TimestampShape shape) {
    return isPrelude(shape)
        ? builtin("timestamp.Timestamp")
        : namedScalar(shape, "timestamp.Timestamp");
  }

  @Override
  public Symbol documentShape(DocumentShape shape) {
    return isPrelude(shape) ? builtin("dynamic.Dynamic") : namedScalar(shape, "dynamic.Dynamic");
  }

  @Override
  public Symbol listShape(ListShape shape) {
    return namedAlias(shape);
  }

  @Override
  public Symbol mapShape(MapShape shape) {
    return namedAlias(shape);
  }

  @Override
  public Symbol unionShape(UnionShape shape) {
    return namedCustomType(shape);
  }

  @Override
  public Symbol structureShape(StructureShape shape) {
    return namedCustomType(shape);
  }

  @Override
  public Symbol memberShape(MemberShape shape) {
    Symbol.Builder builder = toSymbol(model.expectShape(shape.getTarget())).toBuilder();
    Optional.ofNullable(fieldNames.get(shape.getId()))
        .ifPresent(name -> builder.putProperty("fieldName", name));
    Optional.ofNullable(unionTagNames.get(shape.getId()))
        .ifPresent(name -> builder.putProperty("unionTag", name));
    Map<String, String> constructors = constructorNames.get(shape.getContainer());
    if (constructors != null) {
      String memberName = shape.getMemberName();
      Optional.ofNullable(constructors.get(memberName))
          .ifPresent(name -> builder.putProperty("constructorName", name));
    }
    return builder.build();
  }

  @Override
  public Symbol enumShape(EnumShape shape) {
    Map<String, String> constructors =
        constructorNames.getOrDefault(shape.getId(), Map.of());
    return namedCustomType(shape).toBuilder()
        .putProperty("enumConstructors", new ArrayList<>(constructors.values()))
        .putProperty("enumConstructorByMember", constructors)
        .putProperty("unknownConstructor", constructors.get(UNKNOWN_MEMBER))
        .build();
  }

  @Override
  public Symbol intEnumShape(IntEnumShape shape) {
    Map<String, String> constructors =
        constructorNames.getOrDefault(shape.getId(), Map.of());
    return namedCustomType(shape).toBuilder()
        .putProperty("enumConstructors", new ArrayList<>(constructors.values()))
        .putProperty("enumConstructorByMember", constructors)
        .putProperty("unknownConstructor", constructors.get(UNKNOWN_MEMBER))
        .build();
  }

  @Override
  public Symbol serviceShape(ServiceShape shape) {
    BeamGleamLayout layout = new BeamGleamLayout(settings, shape.getId().getNamespace(), service);
    String name =
        switch (kind) {
          case TYPES -> layout.typesModuleName();
          case CLIENT -> layout.clientModuleName();
          case SERVER -> layout.serverModuleName();
        };
    return Symbol.builder()
        .name(name)
        .namespace(moduleNamespace, ".")
        .definitionFile(kind == BeamCodegenKind.TYPES ? "" : definitionFile)
        .putProperty("builtIn", false)
        .putProperty("beamKind", kind.name())
        .build();
  }

  @Override
  public Symbol operationShape(OperationShape shape) {
    return serviceScopedFunctionSymbol(shape);
  }

  @Override
  public Symbol resourceShape(ResourceShape shape) {
    return serviceScopedFunctionSymbol(shape);
  }

  String constructorName(Shape container, String memberName) {
    Map<String, String> constructors = constructorNames.get(container.getId());
    if (constructors == null) {
      return toPascalCase(identifierEscaper.escape(BeamNameUtils.toSnakeCase(memberName)));
    }
    return constructors.getOrDefault(
        memberName,
        toPascalCase(identifierEscaper.escape(BeamNameUtils.toSnakeCase(memberName))));
  }

  String unknownConstructorName(Shape container) {
    return constructorName(container, UNKNOWN_MEMBER);
  }

  static String toPascalCase(String snakeName) {
    // Escaped reserved words end with '_'; CamelCase conversion must keep that marker.
    int trailing = 0;
    for (int i = snakeName.length() - 1; i >= 0 && snakeName.charAt(i) == '_'; i--) {
      trailing++;
    }
    String core = trailing == 0 ? snakeName : snakeName.substring(0, snakeName.length() - trailing);
    return BeamNameUtils.toCamelCaseVariable(core) + "_".repeat(trailing);
  }

  private Symbol builtin(String name) {
    return Symbol.builder()
        .name(name)
        .namespace(moduleNamespace, ".")
        .putProperty("builtIn", true)
        .build();
  }

  private Symbol namedScalar(Shape shape, String baseType) {
    String name = toTypeName(shape);
    if (name.equals(baseType) || BeamGleamBuiltinTypes.shadowsBuiltinTypeName(name)) {
      return builtin(baseType);
    }
    return Symbol.builder()
        .name(name)
        .namespace(moduleNamespace, ".")
        .definitionFile(definitionFile)
        .putProperty("builtIn", false)
        .putProperty("typeKind", "alias")
        .putProperty("baseType", baseType)
        .build();
  }

  private Symbol namedAlias(Shape shape) {
    String name = toTypeName(shape);
    return Symbol.builder()
        .name(name)
        .namespace(moduleNamespace, ".")
        .definitionFile(definitionFile)
        .putProperty("builtIn", false)
        .putProperty("typeKind", "alias")
        .build();
  }

  private Symbol namedCustomType(Shape shape) {
    String name = toTypeName(shape);
    return Symbol.builder()
        .name(name)
        .namespace(moduleNamespace, ".")
        .definitionFile(definitionFile)
        .putProperty("builtIn", false)
        .putProperty("typeKind", "custom")
        .build();
  }

  private boolean isPrelude(Shape shape) {
    return shape.getId().getNamespace().equals("smithy.api");
  }

  private String toTypeName(Shape shape) {
    return typeNames.getOrDefault(
        shape.getId(),
        toPascalCase(
            identifierEscaper.escape(BeamNameUtils.toSnakeCase(shape.getId().getName(service)))));
  }

  private Symbol serviceScopedFunctionSymbol(Shape shape) {
    String name =
        serviceFunctionNames.getOrDefault(
            shape.getId(),
            identifierEscaper.escape(BeamNameUtils.toSnakeCase(shape.getId().getName(service))));
    return Symbol.builder()
        .name(name)
        .namespace(moduleNamespace, ".")
        .definitionFile(kind == BeamCodegenKind.TYPES ? "" : definitionFile)
        .putProperty("builtIn", false)
        .putProperty("beamKind", kind.name())
        .build();
  }

  private Map<ShapeId, String> toPascalTypeNames(Map<ShapeId, String> snakeNames) {
    Map<ShapeId, String> result = new HashMap<>();
    snakeNames.forEach((id, name) -> result.put(id, toPascalCase(name)));
    return Map.copyOf(result);
  }

  private Map<ShapeId, Map<String, String>> buildConstructorNames() {
    List<ConstructorCandidate> candidates = new ArrayList<>();
    for (Shape shape : new Walker(model).walkShapes(service)) {
      if (shape instanceof EnumShape enumShape) {
        Map<String, String> atoms = enumAtomNames.getOrDefault(enumShape.getId(), Map.of());
        for (Map.Entry<String, String> entry : atoms.entrySet()) {
          candidates.add(
              new ConstructorCandidate(
                  enumShape.getId(), entry.getKey(), toPascalCase(entry.getValue()), typeNames.get(enumShape.getId())));
        }
        candidates.add(
            new ConstructorCandidate(
                enumShape.getId(),
                UNKNOWN_MEMBER,
                "Unknown",
                typeNames.get(enumShape.getId())));
      } else if (shape instanceof IntEnumShape intEnumShape) {
        Map<String, String> atoms = enumAtomNames.getOrDefault(intEnumShape.getId(), Map.of());
        for (Map.Entry<String, String> entry : atoms.entrySet()) {
          candidates.add(
              new ConstructorCandidate(
                  intEnumShape.getId(),
                  entry.getKey(),
                  toPascalCase(entry.getValue()),
                  typeNames.get(intEnumShape.getId())));
        }
        candidates.add(
            new ConstructorCandidate(
                intEnumShape.getId(),
                UNKNOWN_MEMBER,
                "Unknown",
                typeNames.get(intEnumShape.getId())));
      } else if (shape instanceof UnionShape unionShape) {
        for (MemberShape member : unionShape.members()) {
          String tag =
              unionTagNames.getOrDefault(
                  member.getId(),
                  identifierEscaper.escape(BeamNameUtils.toSnakeCase(member.getMemberName())));
          candidates.add(
              new ConstructorCandidate(
                  unionShape.getId(),
                  member.getMemberName(),
                  toPascalCase(tag),
                  typeNames.get(unionShape.getId())));
        }
        candidates.add(
            new ConstructorCandidate(
                unionShape.getId(),
                UNKNOWN_MEMBER,
                "Unknown",
                typeNames.get(unionShape.getId())));
      } else if (shape instanceof StructureShape structureShape) {
        // Single-variant structure constructor matches the type name.
        String typeName = typeNames.get(structureShape.getId());
        if (typeName != null) {
          candidates.add(
              new ConstructorCandidate(
                  structureShape.getId(), structureShape.getId().getName(), typeName, typeName));
        }
      }
    }

    Map<String, Integer> shortCounts = new HashMap<>();
    for (ConstructorCandidate candidate : candidates) {
      shortCounts.merge(candidate.shortName(), 1, Integer::sum);
    }

    Map<ShapeId, Map<String, String>> result = new LinkedHashMap<>();
    for (ConstructorCandidate candidate : candidates) {
      String chosen = candidate.shortName();
      if (shortCounts.getOrDefault(chosen, 0) > 1
          || BeamGleamBuiltinTypes.shadowsBuiltinConstructorName(chosen)) {
        chosen = candidate.shapeTypeName() + candidate.shortName();
      }
      result
          .computeIfAbsent(candidate.shapeId(), id -> new LinkedHashMap<>())
          .put(candidate.memberName(), chosen);
    }
    Map<ShapeId, Map<String, String>> copy = new LinkedHashMap<>();
    result.forEach((id, map) -> copy.put(id, Map.copyOf(map)));
    return Map.copyOf(copy);
  }

  private static ReservedWords gleamReservedWords() {
    return new ReservedWordsBuilder()
        .put("as", "as_")
        .put("assert", "assert_")
        .put("auto", "auto_")
        .put("case", "case_")
        .put("const", "const_")
        .put("echo", "echo_")
        .put("else", "else_")
        .put("fn", "fn_")
        .put("if", "if_")
        .put("import", "import_")
        .put("let", "let_")
        .put("opaque", "opaque_")
        .put("panic", "panic_")
        .put("pub", "pub_")
        .put("todo", "todo_")
        .put("type", "type_")
        .put("use", "use_")
        .build();
  }

  private record ConstructorCandidate(
      ShapeId shapeId, String memberName, String shortName, String shapeTypeName) {}
}
