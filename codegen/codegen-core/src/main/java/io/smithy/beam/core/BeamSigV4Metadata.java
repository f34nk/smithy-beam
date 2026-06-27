package io.smithy.beam.core;

import java.util.Optional;
import software.amazon.smithy.aws.traits.auth.SigV4Trait;
import software.amazon.smithy.aws.traits.auth.UnsignedPayloadTrait;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.shapes.OperationShape;
import software.amazon.smithy.model.shapes.ServiceShape;

public record BeamSigV4Metadata(String signingName, boolean unsignedPayload) {

  public static Optional<BeamSigV4Metadata> from(ServiceShape service) {
    if (!service.hasTrait(SigV4Trait.class)) {
      return Optional.empty();
    }
    SigV4Trait trait = service.expectTrait(SigV4Trait.class);
    return Optional.of(
        new BeamSigV4Metadata(
            emptyToDefault(trait.getName(), service.getId().getName()),
            service.hasTrait(UnsignedPayloadTrait.class)));
  }

  public static boolean operationUsesUnsignedPayload(Model model, OperationShape operation) {
    if (operation.hasTrait(UnsignedPayloadTrait.class)) {
      return true;
    }
    return false;
  }

  private static String emptyToDefault(String value, String fallback) {
    return value == null || value.isEmpty() ? fallback : value;
  }
}
