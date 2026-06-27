package io.smithy.beam.erlang;

import io.smithy.beam.core.BeamS3CustomizationIndex;
import software.amazon.smithy.model.shapes.ServiceShape;

/**
 * Emits {@code s3_endpoint.erl} bucket virtual-host and path-style helpers for S3 REST-XML clients.
 */
public final class ErlangS3EndpointEmitter {

    private ErlangS3EndpointEmitter() {}

    public static void emit(ErlangContext ctx, ServiceShape service) {
        if (!BeamS3CustomizationIndex.isS3Service(service)) {
            return;
        }

        ctx.writerDelegator().useFileWriter("s3_endpoint.erl", writer -> {
            writer.write("$L", ErlangS3EndpointIr.s3EndpointModule(service).asString());
        });
    }
}
