package io.smithy.beam.core;

import java.util.List;
import software.amazon.smithy.model.shapes.ShapeId;

public final class BeamProtocolSupport {

  private BeamProtocolSupport() {}

  public static boolean isBuiltinProtocol(ShapeId id) {
    return BeamProtocolIds.REST_JSON_1.equals(id)
        || BeamProtocolIds.AWS_JSON_1_0.equals(id)
        || BeamProtocolIds.AWS_JSON_1_1.equals(id)
        || BeamProtocolIds.AWS_QUERY.equals(id)
        || BeamProtocolIds.EC2_QUERY.equals(id)
        || BeamProtocolIds.REST_XML.equals(id);
  }

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
