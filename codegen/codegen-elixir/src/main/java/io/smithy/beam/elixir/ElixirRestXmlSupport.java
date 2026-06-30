package io.smithy.beam.elixir;

import io.smithy.beam.core.BeamS3CustomizationIndex;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.shapes.ServiceShape;

final class ElixirRestXmlSupport {
  private ElixirRestXmlSupport() {}

  static boolean serviceHasHostLabelOperations(Model model, ServiceShape service) {
    return ElixirRestJsonSupport.serviceHasHostLabelOperations(model, service);
  }

  static boolean serviceEncodesWithConfig(Model model, ServiceShape service) {
    return serviceHasHostLabelOperations(model, service)
        || BeamS3CustomizationIndex.of(model).serviceUsesBucketAddressing(service);
  }
}
