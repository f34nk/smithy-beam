package io.smithy.beam.elixir;

import io.smithy.beam.core.BeamProtocolIds;
import software.amazon.smithy.model.shapes.ServiceShape;

/** EC2 Query protocol codec emitter for Elixir clients. */
public final class ElixirEc2QueryEmitter {

  private ElixirEc2QueryEmitter() {}

  static void emitCodecModule(ElixirContext ctx, ServiceShape service) {
    ElixirAwsQueryEmitter.emitCodecModule(ctx, service, BeamProtocolIds.EC2_QUERY);
  }

  static void emitServerCodecModule(ElixirContext ctx, ServiceShape service) {
    ElixirAwsQueryEmitter.emitServerCodecModule(ctx, service, BeamProtocolIds.EC2_QUERY);
  }
}
