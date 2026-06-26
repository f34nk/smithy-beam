package io.smithy.beam.erlang;

import io.smithy.beam.core.BeamAwsServiceMetadata;
import io.smithy.beam.core.BeamErlangLayout;
import io.smithy.beam.core.BeamProtocolIds;
import io.smithy.beam.ir.erlang.ErlModule;
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
                ErlModule module = ErlangRestJsonIr.buildClientCodecModule(ctx, service);
                ErlangCodecEmission.writeModule(ctx, layout.clientCodecModuleName(id) + ".erl", module);
            }
            case ShapeId id when BeamProtocolIds.AWS_JSON_1_0.equals(id)
                    || BeamProtocolIds.AWS_JSON_1_1.equals(id) -> {
                BeamAwsServiceMetadata.from(service).orElseThrow();
                ErlModule module = ErlangAwsJsonIr.buildClientCodecModule(ctx, service, id);
                ErlangCodecEmission.writeModule(ctx, layout.clientCodecModuleName(id) + ".erl", module);
            }
            case ShapeId id when BeamProtocolIds.AWS_QUERY.equals(id)
                    || BeamProtocolIds.EC2_QUERY.equals(id) -> {
                BeamAwsServiceMetadata.from(service).orElseThrow();
                ErlModule module = ErlangAwsQueryIr.buildClientCodecModule(ctx, service, id);
                ErlangCodecEmission.writeModule(ctx, layout.clientCodecModuleName(id) + ".erl", module);
            }
            case ShapeId id when BeamProtocolIds.REST_XML.equals(id) -> {
                ErlModule module = ErlangRestXmlIr.buildClientCodecModule(ctx, service);
                ErlangCodecEmission.writeModule(ctx, layout.clientCodecModuleName(id) + ".erl", module);
            }
            default -> { /* no codec module for this protocol */ }
        }
    }

    static void emitServerCodec(ErlangContext ctx, ServiceShape service) {
        ShapeId protocol = ctx.resolvedProtocolTraitId();
        if (protocol == null) {
            return;
        }
        ErlangCodecEmission.emitRuntimeHelpersIfNeeded(ctx, service, true);
        BeamErlangLayout layout = layout(ctx, service);
        switch (protocol) {
            case ShapeId id when BeamProtocolIds.REST_JSON_1.equals(id) -> {
                ErlModule module = ErlangRestJsonIr.buildServerCodecModule(ctx, service);
                ErlangCodecEmission.writeModule(ctx, layout.serverCodecModuleName(id) + ".erl", module);
            }
            case ShapeId id when BeamProtocolIds.AWS_JSON_1_0.equals(id)
                    || BeamProtocolIds.AWS_JSON_1_1.equals(id) -> {
                ErlModule module = ErlangAwsJsonIr.buildServerCodecModule(ctx, service, id);
                ErlangCodecEmission.writeModule(ctx, layout.serverCodecModuleName(id) + ".erl", module);
            }
            case ShapeId id when BeamProtocolIds.AWS_QUERY.equals(id)
                    || BeamProtocolIds.EC2_QUERY.equals(id) -> {
                ErlModule module = ErlangAwsQueryIr.buildServerCodecModule(ctx, service, id);
                ErlangCodecEmission.writeModule(ctx, layout.serverCodecModuleName(id) + ".erl", module);
            }
            case ShapeId id when BeamProtocolIds.REST_XML.equals(id) -> {
                ErlModule module = ErlangRestXmlIr.buildServerCodecModule(ctx, service);
                ErlangCodecEmission.writeModule(ctx, layout.serverCodecModuleName(id) + ".erl", module);
            }
            default -> { /* no codec module for this protocol */ }
        }
    }

    private static BeamErlangLayout layout(ErlangContext ctx, ServiceShape service) {
        return new BeamErlangLayout(ctx.settings(), service.getId().getNamespace(), service);
    }
}
