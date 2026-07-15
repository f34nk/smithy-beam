package io.smithy.beam.core;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.knowledge.TopDownIndex;
import software.amazon.smithy.model.shapes.OperationShape;
import software.amazon.smithy.model.shapes.ResourceShape;
import software.amazon.smithy.model.shapes.ServiceShape;
import software.amazon.smithy.model.shapes.ShapeId;

/** Resource-scoped queries built on {@link TopDownIndex}. */
public final class BeamResourceIndex {

  private final Model model;
  private final TopDownIndex topDown;

  private BeamResourceIndex(Model model, TopDownIndex topDown) {
    this.model = model;
    this.topDown = topDown;
  }

  public static BeamResourceIndex of(Model model) {
    return new BeamResourceIndex(model, TopDownIndex.of(model));
  }

  public List<ResourceShape> containedResourcesSorted(ServiceShape service) {
    List<ResourceShape> resources = new ArrayList<>(topDown.getContainedResources(service));
    resources.sort(Comparator.comparing(r -> r.getId().toString()));
    return resources;
  }

  public List<ShapeId> identifierChain(ResourceShape resource) {
    List<ShapeId> chain = new ArrayList<>();
    ResourceShape current = resource;
    while (current != null) {
      chain.add(0, current.toShapeId());
      current = parentResource(current).orElse(null);
    }
    return chain;
  }

  public Optional<ResourceShape> parentResource(ResourceShape resource) {
    ShapeId id = resource.getId();
    return model
        .shapes(ResourceShape.class)
        .filter(candidate -> candidate.getResources().contains(id))
        .findFirst();
  }

  public Map<String, ShapeId> identifiers(ResourceShape resource) {
    return resource.getIdentifiers();
  }

  public OperationShape expectOperation(ShapeId id) {
    return model.expectShape(id, OperationShape.class);
  }

  public Model model() {
    return model;
  }

  public List<OperationShape> collectionOperationsSorted(ResourceShape resource) {
    List<OperationShape> ops = new ArrayList<>();
    for (ShapeId opId : resource.getCollectionOperations()) {
      ops.add(expectOperation(opId));
    }
    ops.sort(Comparator.comparing(o -> o.getId().toString()));
    return ops;
  }
}
