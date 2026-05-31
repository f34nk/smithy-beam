package io.smithy.beam.elixir;

import io.smithy.beam.core.BeamEc2QueryProtocolCodegen;
import software.amazon.smithy.model.shapes.ServiceShape;

/**
 * EC2 Query protocol codec emitter for Elixir clients.
 */
public final class ElixirEc2QueryEmitter {

    private ElixirEc2QueryEmitter() {}

    static void emitCodecModule(ElixirContext ctx, ServiceShape service) {
        ElixirAwsQueryEmitter.emitCodecModule(ctx, service, BeamEc2QueryProtocolCodegen.EC2_QUERY);
    }
}
