package io.smithy.beam.erlang;

import io.smithy.beam.core.BeamEc2QueryProtocolCodegen;
import software.amazon.smithy.model.shapes.ServiceShape;

/**
 * EC2 Query protocol codec emitter for Erlang clients.
 */
public final class ErlangEc2QueryEmitter {

    private ErlangEc2QueryEmitter() {}

    static void emitCodecModule(ErlangContext ctx, ServiceShape service) {
        ErlangAwsQueryEmitter.emitCodecModule(ctx, service, BeamEc2QueryProtocolCodegen.EC2_QUERY);
    }
}
