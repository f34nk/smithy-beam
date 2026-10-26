package io.smithy.beam.gleam;

import io.smithy.beam.core.BeamEventStreamIndex;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.neighbor.Walker;
import software.amazon.smithy.model.shapes.EnumShape;
import software.amazon.smithy.model.shapes.IntEnumShape;
import software.amazon.smithy.model.shapes.MapShape;
import software.amazon.smithy.model.shapes.ServiceShape;
import software.amazon.smithy.model.shapes.Shape;
import software.amazon.smithy.model.shapes.ShapeId;
import software.amazon.smithy.model.shapes.StructureShape;
import software.amazon.smithy.model.shapes.UnionShape;
import software.amazon.smithy.model.traits.ErrorTrait;

final class GleamRestJsonSupport {

  private GleamRestJsonSupport() {}

  static List<EnumShape> reachableEnumShapes(Model model, ServiceShape service) {
    Set<ShapeId> emitted = new LinkedHashSet<>();
    List<EnumShape> shapes = new ArrayList<>();
    for (Shape shape : new Walker(model).walkShapes(service)) {
      if (shape instanceof EnumShape enumShape && emitted.add(enumShape.getId())) {
        shapes.add(enumShape);
      }
    }
    return shapes;
  }

  static List<IntEnumShape> reachableIntEnumShapes(Model model, ServiceShape service) {
    Set<ShapeId> emitted = new LinkedHashSet<>();
    List<IntEnumShape> shapes = new ArrayList<>();
    for (Shape shape : new Walker(model).walkShapes(service)) {
      if (shape instanceof IntEnumShape intEnumShape && emitted.add(intEnumShape.getId())) {
        shapes.add(intEnumShape);
      }
    }
    return shapes;
  }

  static List<UnionShape> reachableUnionShapes(Model model, ServiceShape service) {
    BeamEventStreamIndex eventStreamIndex = BeamEventStreamIndex.of(model);
    Set<ShapeId> emitted = new LinkedHashSet<>();
    List<UnionShape> shapes = new ArrayList<>();
    for (Shape shape : new Walker(model).walkShapes(service)) {
      if (shape instanceof UnionShape union
          && !eventStreamIndex.isEventStreamUnion(union)
          && emitted.add(union.getId())) {
        shapes.add(union);
      }
    }
    return shapes;
  }

  static List<StructureShape> reachableStructureShapes(Model model, ServiceShape service) {
    Set<ShapeId> emitted = new LinkedHashSet<>();
    List<StructureShape> shapes = new ArrayList<>();
    for (Shape shape : new Walker(model).walkShapes(service)) {
      if (shape instanceof StructureShape structure
          && !structure.hasTrait(ErrorTrait.class)
          && emitted.add(structure.getId())) {
        shapes.add(structure);
      }
    }
    return shapes;
  }

  static List<MapShape> reachableTypedMapShapes(Model model, ServiceShape service) {
    Set<ShapeId> emitted = new LinkedHashSet<>();
    List<MapShape> shapes = new ArrayList<>();
    for (Shape shape : new Walker(model).walkShapes(service)) {
      if (shape instanceof MapShape mapShape
          && GleamMapHelperDsl.mapNeedsTypedHelper(model, mapShape)
          && emitted.add(mapShape.getId())) {
        shapes.add(mapShape);
      }
    }
    return shapes;
  }
}
