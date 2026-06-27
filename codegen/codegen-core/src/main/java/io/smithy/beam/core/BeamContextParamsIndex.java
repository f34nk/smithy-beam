package io.smithy.beam.core;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import software.amazon.smithy.model.shapes.OperationShape;
import software.amazon.smithy.model.shapes.ServiceShape;
import software.amazon.smithy.rulesengine.traits.ClientContextParamsTrait;
import software.amazon.smithy.rulesengine.traits.OperationContextParamsTrait;
import software.amazon.smithy.rulesengine.traits.StaticContextParamsTrait;

/** Resolves endpoint rule context parameters for generated endpoint resolver modules. */
public final class BeamContextParamsIndex {

  private BeamContextParamsIndex() {}

  public static List<String> clientContextParamNames(ServiceShape service) {
    return service
        .getTrait(ClientContextParamsTrait.class)
        .map(trait -> List.copyOf(trait.getParameters().keySet()))
        .orElse(List.of());
  }

  public static Map<String, String> clientContextConfigKeys(ServiceShape service) {
    Map<String, String> keys = new LinkedHashMap<>();
    for (String paramName : clientContextParamNames(service)) {
      keys.put(paramName, BeamNameUtils.toSnakeCase(paramName));
    }
    return Collections.unmodifiableMap(keys);
  }

  public static Optional<StaticContextParamsTrait> staticContextParams(OperationShape operation) {
    return operation.getTrait(StaticContextParamsTrait.class);
  }

  public static Optional<OperationContextParamsTrait> operationContextParams(
      OperationShape operation) {
    return operation.getTrait(OperationContextParamsTrait.class);
  }
}
