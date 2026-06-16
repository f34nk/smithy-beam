package io.smithy.beam.erlang;

import io.smithy.beam.core.BeamProtocolIds;
import software.amazon.smithy.model.shapes.ServiceShape;

/**
 * EC2 Query protocol codec emitter for Erlang clients.
 */
public final class ErlangEc2QueryEmitter {

    private ErlangEc2QueryEmitter() {}

    static void emitCodecModule(ErlangContext ctx, ServiceShape service) {
        ErlangAwsQueryEmitter.emitCodecModule(ctx, service, BeamProtocolIds.EC2_QUERY);
    }

    static void emitServerCodecModule(ErlangContext ctx, ServiceShape service) {
        ErlangAwsQueryEmitter.emitServerCodecModule(ctx, service, BeamProtocolIds.EC2_QUERY);
    }
}
