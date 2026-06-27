package io.smithy.beam.elixir;

import io.smithy.beam.core.BeamProtocolIds;
import software.amazon.smithy.model.shapes.ServiceShape;

/** AWS JSON 1.0 RPC codec emitter for Elixir. */
public final class ElixirAwsJson10Emitter {

  public static final String CONTENT_TYPE = "application/x-amz-json-1.0";

  private ElixirAwsJson10Emitter() {}

  public static void emitCodecModule(ElixirContext ctx, ServiceShape service) {
    ElixirAwsJsonRpcEmitter.emitCodecModule(
        ctx, service, BeamProtocolIds.AWS_JSON_1_0, CONTENT_TYPE, "1.0");
  }

  public static void emitServerCodecModule(ElixirContext ctx, ServiceShape service) {
    ElixirAwsJsonRpcEmitter.emitServerCodecModule(
        ctx, service, BeamProtocolIds.AWS_JSON_1_0, CONTENT_TYPE, "1.0");
  }
}
