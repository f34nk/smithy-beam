package io.smithy.beam.core;

import java.util.List;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.knowledge.TopDownIndex;
import software.amazon.smithy.model.shapes.OperationShape;
import software.amazon.smithy.model.shapes.ResourceShape;
import software.amazon.smithy.model.shapes.ServiceShape;

/**
 * Thin wrapper around {@link TopDownIndex} for service-scoped queries used by client and server
 * DirectedCodegen implementations.
 */
public final class BeamServiceIndex {

  private final TopDownIndex index;

  private BeamServiceIndex(TopDownIndex index) {
    this.index = index;
  }

  public static BeamServiceIndex of(Model model) {
    return new BeamServiceIndex(TopDownIndex.of(model));
  }

  public List<OperationShape> containedOperations(ServiceShape service) {
    return List.copyOf(index.getContainedOperations(service));
  }

  public List<ResourceShape> containedResources(ServiceShape service) {
    return List.copyOf(index.getContainedResources(service));
  }
}
