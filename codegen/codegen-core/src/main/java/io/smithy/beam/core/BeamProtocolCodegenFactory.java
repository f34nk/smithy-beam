package io.smithy.beam.core;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import software.amazon.smithy.codegen.core.CodegenException;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.shapes.ShapeId;

public final class BeamProtocolCodegenFactory {

  private static final Set<ShapeId> BUILTIN_PROTOCOLS =
      Set.of(
          BeamProtocolIds.REST_JSON_1,
          BeamProtocolIds.REST_XML,
          BeamProtocolIds.AWS_JSON_1_0,
          BeamProtocolIds.AWS_JSON_1_1,
          BeamProtocolIds.AWS_QUERY,
          BeamProtocolIds.EC2_QUERY);

  private BeamProtocolCodegenFactory() {}

  public static BeamProtocolCodegen create(
      Model model,
      ShapeId resolvedProtocolTraitId,
      List<? extends BeamProtocolIntegration> integrations) {
    Objects.requireNonNull(resolvedProtocolTraitId, "resolvedProtocolTraitId");
    if (BUILTIN_PROTOCOLS.contains(resolvedProtocolTraitId)) {
      return new BeamNoOpProtocolCodegen(resolvedProtocolTraitId);
    }
    for (BeamProtocolIntegration integration : integrations) {
      Optional<BeamProtocolCodegen> custom =
          integration.createProtocolCodegen(model, resolvedProtocolTraitId);
      if (custom.isPresent()) {
        return custom.get();
      }
    }
    throw new CodegenException(
        "No BeamProtocolCodegen registered for protocol trait " + resolvedProtocolTraitId);
  }
}
