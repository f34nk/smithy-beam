package io.smithy.beam.erlang;

import io.smithy.beam.core.BeamProtocolIds;
import software.amazon.smithy.model.shapes.ServiceShape;

/**
 * AWS JSON 1.0 RPC codec emitter for Erlang. POST / with X-Amz-Target and JSON body.
 */
public final class ErlangAwsJson10Emitter {

    public static final String CONTENT_TYPE = "application/x-amz-json-1.0";

    private ErlangAwsJson10Emitter() {}

    public static void emitServerCodecModule(ErlangContext ctx, ServiceShape service) {
        ErlangAwsJsonRpcEmitter.emitServerCodecModule(
                ctx, service, BeamProtocolIds.AWS_JSON_1_0, CONTENT_TYPE, "1.0");
    }

    public static void emitCodecModule(ErlangContext ctx, ServiceShape service) {
        ErlangAwsJsonRpcEmitter.emitCodecModule(
                ctx, service, BeamProtocolIds.AWS_JSON_1_0, CONTENT_TYPE, "1.0");
    }
}
