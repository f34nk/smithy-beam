package io.smithy.beam.core;

import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.shapes.OperationShape;
import software.amazon.smithy.model.shapes.ShapeId;
import software.amazon.smithy.model.shapes.StructureShape;

public final class BeamClientRetrySupport {

  private BeamClientRetrySupport() {}

  public static boolean operationHasRetryableErrors(Model model, OperationShape operation) {
    for (ShapeId errorId : operation.getErrors()) {
      StructureShape error = model.expectShape(errorId, StructureShape.class);
      if (BeamRetryIndex.forError(error).map(BeamRetryIndex.RetryInfo::retryable).orElse(false)) {
        return true;
      }
    }
    return false;
  }
}
