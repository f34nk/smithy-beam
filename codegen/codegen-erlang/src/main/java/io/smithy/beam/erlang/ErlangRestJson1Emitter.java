package io.smithy.beam.erlang;

import io.smithy.beam.core.BeamErlangLayout;
import software.amazon.smithy.model.shapes.ServiceShape;

/**
 * REST JSON codec scaffolding for Erlang. Keeps {@code codegen-core} free of Erlang imports.
 */
public final class ErlangRestJson1Emitter {

    private ErlangRestJson1Emitter() {}

    public static void emitStubModule(ErlangContext ctx, ServiceShape service) {
        String ns = service.getId().getNamespace();
        BeamErlangLayout layout = new BeamErlangLayout(ctx.settings(), ns);
        ctx.writerDelegator().useFileWriter(layout.codecModuleFile(), writer -> {
            writer.write("%% REST JSON codecs for $L (generated).", service.getId());
            writer.write("-module($L).", layout.codecModuleName());
            writer.write("-export([]).");
            writer.write(
                    "%% TODO: generate encode_/decode_ pairs for structures reachable from HTTP payloads.");
        });
    }
}
