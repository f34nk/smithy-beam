package io.smithy.beam.gleam;

import io.smithy.beam.core.BeamProtocolIds;
import software.amazon.smithy.model.shapes.ServiceShape;
import software.amazon.smithy.model.shapes.ShapeId;

final class GleamProtocolCodecDsl {

  private GleamProtocolCodecDsl() {}

  static void emitClientCodec(GleamContext ctx, ServiceShape service) {
    ShapeId protocol = ctx.resolvedProtocolTraitId();
    if (protocol == null) {
      return;
    }
    if (BeamProtocolIds.REST_JSON_1.equals(protocol)) {
      GleamRestJsonDsl.emitClientCodecModule(ctx, service);
    }
  }

  static void emitServerCodec(GleamContext ctx, ServiceShape service) {
    ShapeId protocol = ctx.resolvedProtocolTraitId();
    if (protocol == null) {
      return;
    }
    if (BeamProtocolIds.REST_JSON_1.equals(protocol)) {
      GleamRestJsonDsl.emitServerCodecModule(ctx, service);
    }
  }
}
