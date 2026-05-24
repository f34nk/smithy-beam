package io.smithy.beam.elixir;

import io.smithy.beam.core.BeamElixirLayout;
import software.amazon.smithy.model.shapes.ServiceShape;

/**
 * REST JSON codec scaffolding for Elixir. Keeps {@code codegen-core} free of Elixir imports.
 */
public final class ElixirRestJson1Emitter {

    private ElixirRestJson1Emitter() {}

    public static void emitStubModule(ElixirContext ctx, ServiceShape service) {
        String ns = service.getId().getNamespace();
        BeamElixirLayout layout = new BeamElixirLayout(ctx.settings(), ns);
        String codecFile = layout.restJson1ModuleFile();
        String codecModule = ElixirSymbolProvider.toModuleName(layout.modulePrefix() + "_rest_json_1");
        ctx.writerDelegator().useFileWriter(codecFile, writer -> {
            writer.write("# REST JSON codecs for $L (generated).", service.getId());
            writer.write("defmodule $L do", codecModule);
            writer.indent();
            writer.write(
                    "# TODO: generate encode_/decode_ pairs for structures reachable from HTTP payloads.");
            writer.dedent();
            writer.write("end");
        });
    }
}
