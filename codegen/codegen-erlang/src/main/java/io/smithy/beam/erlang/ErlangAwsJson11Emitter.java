package io.smithy.beam.erlang;

import io.smithy.beam.core.BeamAwsJson11ProtocolCodegen;
import software.amazon.smithy.model.shapes.ServiceShape;

/**
 * AWS JSON 1.1 RPC codec emitter for Erlang. POST / with X-Amz-Target and JSON body.
 */
public final class ErlangAwsJson11Emitter {

    public static final String CONTENT_TYPE = "application/x-amz-json-1.1";

    private ErlangAwsJson11Emitter() {}

    public static void emitServerCodecModule(ErlangContext ctx, ServiceShape service) {
        ErlangAwsJsonRpcEmitter.emitServerCodecModule(
                ctx, service, BeamAwsJson11ProtocolCodegen.AWS_JSON_1_1, CONTENT_TYPE, "1.1");
    }

    public static void emitCodecModule(ErlangContext ctx, ServiceShape service) {
        ErlangAwsJsonRpcEmitter.emitCodecModule(
                ctx, service, BeamAwsJson11ProtocolCodegen.AWS_JSON_1_1, CONTENT_TYPE, "1.1");
    }
}
