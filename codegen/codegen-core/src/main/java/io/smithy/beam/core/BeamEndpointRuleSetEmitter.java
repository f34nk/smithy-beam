package io.smithy.beam.core;

import java.util.Optional;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.shapes.ServiceShape;
import software.amazon.smithy.model.shapes.ShapeId;
import software.amazon.smithy.rulesengine.traits.EndpointRuleSetTrait;

/** Resolves and serializes endpoint rule sets for generated BEAM clients. */
public final class BeamEndpointRuleSetEmitter {

  private BeamEndpointRuleSetEmitter() {}

  public static boolean hasRuleSet(Model model, ServiceShape service) {
    return resolveRuleSetTrait(model, service).isPresent();
  }

  public static Optional<String> serializeRuleSetErlangMap(Model model, ServiceShape service) {
    return resolveRuleSetTrait(model, service).map(RuleSetSerializer::toErlangMap);
  }

  public static Optional<String> serializeRuleSetElixirMap(Model model, ServiceShape service) {
    return resolveRuleSetTrait(model, service).map(RuleSetSerializer::toElixirMap);
  }

  public static Optional<String> serializeRuleSetJson(Model model, ServiceShape service) {
    return resolveRuleSetTrait(model, service).map(trait -> RuleSetSerializer.toJson(model, trait));
  }

  public static Optional<EndpointRuleSetTrait> resolveRuleSetTrait(
      Model model, ServiceShape service) {
    return service
        .getTrait(EndpointRuleSetTrait.class)
        .or(() -> indirectRuleSetTrait(model, service));
  }

  private static Optional<EndpointRuleSetTrait> indirectRuleSetTrait(
      Model model, ServiceShape service) {
    return getEndpointRuleSetShapeId(model, service)
        .flatMap(id -> model.getShape(id))
        .flatMap(shape -> shape.getTrait(EndpointRuleSetTrait.class));
  }

  private static Optional<ShapeId> getEndpointRuleSetShapeId(Model model, ServiceShape service) {
    if (service.hasTrait(EndpointRuleSetTrait.class)) {
      return Optional.of(service.getId());
    }
    String namespace = service.getId().getNamespace();
    for (ServiceShape candidate : model.getServiceShapes()) {
      if (candidate.getId().getNamespace().equals(namespace)
          && candidate.hasTrait(EndpointRuleSetTrait.class)) {
        return Optional.of(candidate.getId());
      }
    }
    return Optional.empty();
  }
}
