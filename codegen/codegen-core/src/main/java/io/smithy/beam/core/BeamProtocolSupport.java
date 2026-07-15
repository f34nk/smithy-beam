package io.smithy.beam.core;

import java.util.List;
import software.amazon.smithy.model.shapes.ShapeId;

public final class BeamProtocolSupport {

  private BeamProtocolSupport() {}

  public static boolean hasWireCodegen(
      ShapeId protocol,
      BeamProtocolCodegen protocolCodegen,
      List<? extends BeamProtocolIntegration> integrations) {
    if (protocol == null) {
      return false;
    }
    if (protocolCodegen != null) {
      return true;
    }
    return integrations.stream().anyMatch(i -> i.emitsWireCodecs(protocol));
  }
}
