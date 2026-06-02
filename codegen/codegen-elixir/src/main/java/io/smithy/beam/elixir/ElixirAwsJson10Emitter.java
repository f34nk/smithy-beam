package io.smithy.beam.elixir;

import io.smithy.beam.core.BeamAwsJson10ProtocolCodegen;
import software.amazon.smithy.model.shapes.ServiceShape;

/**
 * AWS JSON 1.0 RPC codec emitter for Elixir.
 */
public final class ElixirAwsJson10Emitter {

    public static final String CONTENT_TYPE = "application/x-amz-json-1.0";

    private ElixirAwsJson10Emitter() {}

    public static void emitCodecModule(ElixirContext ctx, ServiceShape service) {
        ElixirAwsJsonRpcEmitter.emitCodecModule(
                ctx, service, BeamAwsJson10ProtocolCodegen.AWS_JSON_1_0, CONTENT_TYPE, "1.0");
    }

    public static void emitServerCodecModule(ElixirContext ctx, ServiceShape service) {
        ElixirAwsJsonRpcEmitter.emitServerCodecModule(
                ctx, service, BeamAwsJson10ProtocolCodegen.AWS_JSON_1_0, CONTENT_TYPE, "1.0");
    }
}
