package io.smithy.beam.core;

import java.util.Optional;
import software.amazon.smithy.model.shapes.OperationShape;
import software.amazon.smithy.model.traits.RequestCompressionTrait;

/** Resolves {@code smithy.api#requestCompression} for codec emitters. */
public final class BeamRequestCompressionIndex {

  private BeamRequestCompressionIndex() {}

  public static Optional<RequestCompressionTrait> forOperation(OperationShape operation) {
    return operation.getTrait(RequestCompressionTrait.class);
  }
}
