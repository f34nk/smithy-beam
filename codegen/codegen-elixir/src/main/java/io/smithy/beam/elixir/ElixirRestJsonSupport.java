package io.smithy.beam.elixir;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.neighbor.Walker;
import software.amazon.smithy.model.shapes.EnumShape;
import software.amazon.smithy.model.shapes.IntEnumShape;
import software.amazon.smithy.model.shapes.ServiceShape;
import software.amazon.smithy.model.shapes.Shape;
import software.amazon.smithy.model.shapes.ShapeId;

final class ElixirRestJsonSupport {
  private ElixirRestJsonSupport() {}

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
}
