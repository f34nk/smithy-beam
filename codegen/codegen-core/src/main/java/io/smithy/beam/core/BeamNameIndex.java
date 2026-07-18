package io.smithy.beam.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import software.amazon.smithy.codegen.core.ReservedWords;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.neighbor.Walker;
import software.amazon.smithy.model.shapes.EnumShape;
import software.amazon.smithy.model.shapes.IntEnumShape;
import software.amazon.smithy.model.shapes.MemberShape;
import software.amazon.smithy.model.shapes.ServiceShape;
import software.amazon.smithy.model.shapes.Shape;
import software.amazon.smithy.model.shapes.ShapeId;
import software.amazon.smithy.model.shapes.StructureShape;
import software.amazon.smithy.model.shapes.UnionShape;

/**
 * Service-scoped escaped name indexes built in one model walk.
 *
 * <p>Language symbol providers supply {@link ReservedWords} escapers and use the resulting maps for
 * type, field, tag, enum atom, and function names.
 */
public final class BeamNameIndex {

  /** Language-specific reserved-word escapers used while indexing. */
  public record Escapers(
      ReservedWords typeName,
      ReservedWords fieldName,
      ReservedWords atom,
      ReservedWords functionName,
      ReservedWords moduleName) {

    public Escapers {
      if (typeName == null || fieldName == null || atom == null || functionName == null) {
        throw new NullPointerException("type, field, atom, and function escapers are required");
      }
    }

    /** Escapers without module-name indexing (Erlang). */
    public static Escapers of(
        ReservedWords typeName,
        ReservedWords fieldName,
        ReservedWords atom,
        ReservedWords functionName) {
      return new Escapers(typeName, fieldName, atom, functionName, null);
    }

    /** Escapers including module-name indexing (Elixir). */
    public static Escapers withModuleNames(
        ReservedWords typeName,
        ReservedWords fieldName,
        ReservedWords atom,
        ReservedWords functionName,
        ReservedWords moduleName) {
      if (moduleName == null) {
        throw new NullPointerException("moduleName escaper is required");
      }
      return new Escapers(typeName, fieldName, atom, functionName, moduleName);
    }
  }

  private final Map<ShapeId, String> typeNames;
  private final Map<ShapeId, String> fieldNames;
  private final Map<ShapeId, String> unionTagNames;
  private final Map<ShapeId, Map<String, String>> enumAtomNames;
  private final Map<ShapeId, String> serviceFunctionNames;
  private final Map<ShapeId, String> moduleNames;

  private BeamNameIndex(
      Map<ShapeId, String> typeNames,
      Map<ShapeId, String> fieldNames,
      Map<ShapeId, String> unionTagNames,
      Map<ShapeId, Map<String, String>> enumAtomNames,
      Map<ShapeId, String> serviceFunctionNames,
      Map<ShapeId, String> moduleNames) {
    this.typeNames = Map.copyOf(typeNames);
    this.fieldNames = Map.copyOf(fieldNames);
    this.unionTagNames = Map.copyOf(unionTagNames);
    this.enumAtomNames = copyEnumAtoms(enumAtomNames);
    this.serviceFunctionNames = Map.copyOf(serviceFunctionNames);
    this.moduleNames = Map.copyOf(moduleNames);
  }

  public static BeamNameIndex of(Model model, ServiceShape service, Escapers escapers) {
    Set<Shape> closure = new Walker(model).walkShapes(service);
    List<Shape> sorted =
        closure.stream().sorted(Comparator.comparing(shape -> shape.getId().toString())).toList();

    List<Shape> nonPrelude = new ArrayList<>();
    List<Shape> moduleShapes = new ArrayList<>();
    List<Shape> serviceFunctions = new ArrayList<>();
    List<StructureShape> structures = new ArrayList<>();
    List<UnionShape> unions = new ArrayList<>();
    List<EnumShape> enums = new ArrayList<>();
    List<IntEnumShape> intEnums = new ArrayList<>();

    for (Shape shape : sorted) {
      if (isPrelude(shape)) {
        continue;
      }
      nonPrelude.add(shape);
      if (shape.isStructureShape()) {
        StructureShape structure = shape.asStructureShape().orElseThrow();
        structures.add(structure);
        moduleShapes.add(structure);
      } else if (shape.isUnionShape()) {
        unions.add(shape.asUnionShape().orElseThrow());
      } else if (shape.isEnumShape()) {
        EnumShape enumShape = shape.asEnumShape().orElseThrow();
        enums.add(enumShape);
        moduleShapes.add(enumShape);
      } else if (shape.isIntEnumShape()) {
        IntEnumShape intEnum = shape.asIntEnumShape().orElseThrow();
        intEnums.add(intEnum);
        moduleShapes.add(intEnum);
      }
      if (shape.isOperationShape() || shape.isResourceShape()) {
        serviceFunctions.add(shape);
      }
    }

    Map<ShapeId, String> typeNames =
        indexShapeNames(
            nonPrelude,
            shape ->
                escapers
                    .typeName()
                    .escape(BeamNameUtils.toSnakeCase(shape.getId().getName(service))));

    Map<ShapeId, String> fieldNames = new HashMap<>();
    for (StructureShape structure : structures) {
      fieldNames.putAll(
          indexMemberNames(
              new ArrayList<>(structure.members()),
              member ->
                  escapers
                      .fieldName()
                      .escape(BeamNameUtils.toSnakeCase(memberBaseName(member, service)))));
    }

    Map<ShapeId, String> unionTagNames = new HashMap<>();
    for (UnionShape union : unions) {
      unionTagNames.putAll(
          indexMemberNames(
              new ArrayList<>(union.members()),
              member ->
                  escapers
                      .atom()
                      .escape(BeamNameUtils.toSnakeCase(memberBaseName(member, service)))));
    }

    Map<ShapeId, Map<String, String>> enumAtomNames = new HashMap<>();
    for (EnumShape enumShape : enums) {
      enumAtomNames.put(
          enumShape.getId(),
          BeamNameUtils.deconflict(
              enumShape.getEnumValues().keySet().stream().toList(),
              name -> escapers.atom().escape(BeamNameUtils.toSnakeCase(name))));
    }
    for (IntEnumShape intEnum : intEnums) {
      enumAtomNames.put(
          intEnum.getId(),
          BeamNameUtils.deconflict(
              intEnum.getEnumValues().keySet().stream().toList(),
              name -> escapers.atom().escape(BeamNameUtils.toSnakeCase(name))));
    }

    Map<ShapeId, String> serviceFunctionNames =
        indexShapeNames(
            serviceFunctions,
            shape ->
                escapers
                    .functionName()
                    .escape(BeamNameUtils.toSnakeCase(shape.getId().getName(service))));

    Map<ShapeId, String> moduleNames = Map.of();
    if (escapers.moduleName() != null) {
      moduleNames =
          indexShapeNames(
              moduleShapes,
              shape ->
                  escapers
                      .moduleName()
                      .escape(BeamNameUtils.toSnakeCase(shape.getId().getName(service))));
    }

    return new BeamNameIndex(
        typeNames, fieldNames, unionTagNames, enumAtomNames, serviceFunctionNames, moduleNames);
  }

  public Map<ShapeId, String> typeNames() {
    return typeNames;
  }

  public Map<ShapeId, String> fieldNames() {
    return fieldNames;
  }

  public Map<ShapeId, String> unionTagNames() {
    return unionTagNames;
  }

  public Map<ShapeId, Map<String, String>> enumAtomNames() {
    return enumAtomNames;
  }

  public Map<ShapeId, String> serviceFunctionNames() {
    return serviceFunctionNames;
  }

  /** Snake_case module base names when a module escaper was supplied; otherwise empty. */
  public Map<ShapeId, String> moduleNames() {
    return moduleNames;
  }

  private static boolean isPrelude(Shape shape) {
    return shape.getId().getNamespace().equals("smithy.api");
  }

  private static String memberBaseName(MemberShape member, ServiceShape service) {
    ShapeId id = member.getId();
    return id.getMember().orElseGet(() -> id.getName(service));
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

  private static Map<ShapeId, Map<String, String>> copyEnumAtoms(
      Map<ShapeId, Map<String, String>> enumAtomNames) {
    Map<ShapeId, Map<String, String>> copy = new HashMap<>();
    enumAtomNames.forEach(
        (id, atoms) -> copy.put(id, Collections.unmodifiableMap(new LinkedHashMap<>(atoms))));
    return Map.copyOf(copy);
  }
}
