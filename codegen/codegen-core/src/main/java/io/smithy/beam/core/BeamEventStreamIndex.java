package io.smithy.beam.core;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.neighbor.Walker;
import software.amazon.smithy.model.shapes.MemberShape;
import software.amazon.smithy.model.shapes.ServiceShape;
import software.amazon.smithy.model.shapes.Shape;
import software.amazon.smithy.model.shapes.UnionShape;
import software.amazon.smithy.model.traits.StreamingTrait;

/** Detects {@code @streaming} event stream union shapes in a service closure. */
public final class BeamEventStreamIndex {

  private final Model model;

  private BeamEventStreamIndex(Model model) {
    this.model = model;
  }

  public static BeamEventStreamIndex of(Model model) {
    return new BeamEventStreamIndex(model);
  }

  public boolean isEventStreamUnion(Shape shape) {
    return shape.isUnionShape() && shape.getTrait(StreamingTrait.class).isPresent();
  }

  public boolean isEventStreamMember(MemberShape member) {
    return isEventStreamUnion(model.expectShape(member.getTarget()));
  }

  public List<UnionShape> eventStreamUnions(ServiceShape service) {
    Walker walker = new Walker(model);
    Set<Shape> closure = walker.walkShapes(service);
    List<UnionShape> unions = new ArrayList<>();
    for (Shape shape : closure) {
      if (shape instanceof UnionShape union && isEventStreamUnion(union)) {
        unions.add(union);
      }
    }
    unions.sort(Comparator.comparing(u -> u.getId().getName()));
    return List.copyOf(unions);
  }

  public boolean serviceHasEventStreams(ServiceShape service) {
    return !eventStreamUnions(service).isEmpty();
  }
}
