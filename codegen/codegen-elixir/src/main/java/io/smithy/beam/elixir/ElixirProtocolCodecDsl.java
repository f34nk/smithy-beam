package io.smithy.beam.elixir;

import io.smithy.beam.core.BeamAwsServiceMetadata;
import io.smithy.beam.core.BeamProtocolIds;
import software.amazon.smithy.model.shapes.ServiceShape;
import software.amazon.smithy.model.shapes.ShapeId;

final class ElixirProtocolCodecDsl {

  private ElixirProtocolCodecDsl() {}

  static void emitClientCodec(ElixirContext ctx, ServiceShape service) {
    ShapeId protocol = ctx.resolvedProtocolTraitId();
    if (protocol == null) {
      return;
    }
    switch (protocol) {
      case ShapeId id when BeamProtocolIds.REST_JSON_1.equals(id) ->
          ElixirRestJsonDsl.emitClientCodecModule(ctx, service);
      case ShapeId id when BeamProtocolIds.AWS_JSON_1_0.equals(id)
          || BeamProtocolIds.AWS_JSON_1_1.equals(id) -> {
        BeamAwsServiceMetadata.from(service).orElseThrow();
        ElixirAwsJsonDsl.emitClientCodecModule(ctx, service, id);
      }
      case ShapeId id when BeamProtocolIds.AWS_QUERY.equals(id)
          || BeamProtocolIds.EC2_QUERY.equals(id) -> {
        BeamAwsServiceMetadata.from(service).orElseThrow();
        ElixirAwsQueryDsl.emitClientCodecModule(ctx, service, id);
      }
      case ShapeId id when BeamProtocolIds.REST_XML.equals(id) ->
          ElixirRestXmlDsl.emitClientCodecModule(ctx, service);
      default -> {
        /* no codec module for this protocol */
      }
    }
  }

  static void emitServerCodec(ElixirContext ctx, ServiceShape service) {
    ShapeId protocol = ctx.resolvedProtocolTraitId();
    if (protocol == null) {
      return;
    }
    switch (protocol) {
      case ShapeId id when BeamProtocolIds.REST_JSON_1.equals(id) ->
          ElixirRestJsonDsl.emitServerCodecModule(ctx, service);
      case ShapeId id when BeamProtocolIds.AWS_JSON_1_0.equals(id)
              || BeamProtocolIds.AWS_JSON_1_1.equals(id) ->
          ElixirAwsJsonDsl.emitServerCodecModule(ctx, service, id);
      case ShapeId id when BeamProtocolIds.AWS_QUERY.equals(id)
              || BeamProtocolIds.EC2_QUERY.equals(id) ->
          ElixirAwsQueryDsl.emitServerCodecModule(ctx, service, id);
      case ShapeId id when BeamProtocolIds.REST_XML.equals(id) ->
          ElixirRestXmlDsl.emitServerCodecModule(ctx, service);
      default -> {
        /* no codec module for this protocol */
      }
    }
  }
}
