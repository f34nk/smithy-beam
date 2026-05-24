package io.smithy.beam.erlang;

import software.amazon.smithy.model.shapes.ServiceShape;

/**
 * REST JSON codec scaffolding for Erlang. Keeps {@code codegen-core} free of Erlang imports.
 */
public final class ErlangRestJson1Emitter {

    private ErlangRestJson1Emitter() {}

    public static void emitStubModule(ErlangContext ctx, ServiceShape service) {
        String ns = service.getId().getNamespace();
        String module = ctx.settings().resolveModule(ns);
        String codecModule = module + "_rest_json_1";
        ctx.writerDelegator().useFileWriter(codecModule + ".erl", writer -> {
            writer.write("%% REST JSON codecs for $L (generated).", service.getId());
            writer.write("-module($L).", codecModule);
            writer.write("-export([]).");
            writer.write(
                    "%% TODO: generate encode_/decode_ pairs for structures reachable from HTTP payloads.");
        });
    }
}
