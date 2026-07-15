package io.smithy.beam.erlang;

import io.beam.dsl.erlang.Module;
import io.smithy.beam.core.BeamAwsServiceMetadata;
import io.smithy.beam.core.BeamErlangLayout;
import io.smithy.beam.core.BeamProtocolIds;
import software.amazon.smithy.model.shapes.ServiceShape;
import software.amazon.smithy.model.shapes.ShapeId;

final class ErlangProtocolCodecIr {

  private ErlangProtocolCodecIr() {}

  static void emitClientCodec(ErlangContext ctx, ServiceShape service) {
    ShapeId protocol = ctx.resolvedProtocolTraitId();
    if (protocol == null) {
      return;
    }
    BeamErlangLayout layout = layout(ctx, service);
    switch (protocol) {
      case ShapeId id when BeamProtocolIds.REST_JSON_1.equals(id) -> {
        Module module = ErlangRestJsonIr.buildClientCodecModule(ctx, service);
        ErlangCodecEmission.writeModule(ctx, layout.clientCodecModuleName(id) + ".erl", module);
      }
      case ShapeId id when BeamProtocolIds.AWS_JSON_1_0.equals(id)
          || BeamProtocolIds.AWS_JSON_1_1.equals(id) -> {
        BeamAwsServiceMetadata.from(service).orElseThrow();
        Module module = ErlangAwsJsonIr.buildClientCodecModule(ctx, service, id);
        ErlangCodecEmission.writeModule(ctx, layout.clientCodecModuleName(id) + ".erl", module);
      }
      case ShapeId id when BeamProtocolIds.AWS_QUERY.equals(id)
          || BeamProtocolIds.EC2_QUERY.equals(id) -> {
        BeamAwsServiceMetadata.from(service).orElseThrow();
        Module module = ErlangAwsQueryIr.buildClientCodecModule(ctx, service, id);
        ErlangCodecEmission.writeModule(ctx, layout.clientCodecModuleName(id) + ".erl", module);
      }
      case ShapeId id when BeamProtocolIds.REST_XML.equals(id) -> {
        Module module = ErlangRestXmlIr.buildClientCodecModule(ctx, service);
        ErlangCodecEmission.writeModule(ctx, layout.clientCodecModuleName(id) + ".erl", module);
      }
      default -> {
        /* no codec module for this protocol */
      }
    }
  }

  static void emitServerCodec(ErlangContext ctx, ServiceShape service) {
    ShapeId protocol = ctx.resolvedProtocolTraitId();
    if (protocol == null) {
      return;
    }
    BeamErlangLayout layout = layout(ctx, service);
    switch (protocol) {
      case ShapeId id when BeamProtocolIds.REST_JSON_1.equals(id) -> {
        Module module = ErlangRestJsonIr.buildServerCodecModule(ctx, service);
        ErlangCodecEmission.writeModule(ctx, layout.serverCodecModuleName(id) + ".erl", module);
      }
      case ShapeId id when BeamProtocolIds.AWS_JSON_1_0.equals(id)
          || BeamProtocolIds.AWS_JSON_1_1.equals(id) -> {
        Module module = ErlangAwsJsonIr.buildServerCodecModule(ctx, service, id);
        ErlangCodecEmission.writeModule(ctx, layout.serverCodecModuleName(id) + ".erl", module);
      }
      case ShapeId id when BeamProtocolIds.AWS_QUERY.equals(id)
          || BeamProtocolIds.EC2_QUERY.equals(id) -> {
        Module module = ErlangAwsQueryIr.buildServerCodecModule(ctx, service, id);
        ErlangCodecEmission.writeModule(ctx, layout.serverCodecModuleName(id) + ".erl", module);
      }
      case ShapeId id when BeamProtocolIds.REST_XML.equals(id) -> {
        Module module = ErlangRestXmlIr.buildServerCodecModule(ctx, service);
        ErlangCodecEmission.writeModule(ctx, layout.serverCodecModuleName(id) + ".erl", module);
      }
      default -> {
        /* no codec module for this protocol */
      }
    }
  }

  private static BeamErlangLayout layout(ErlangContext ctx, ServiceShape service) {
    return new BeamErlangLayout(ctx.settings(), service.getId().getNamespace(), service);
  }
}
