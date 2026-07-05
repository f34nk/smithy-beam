package io.smithy.beam.erlang;

import io.beam.ir.erlang.ErlangRenderer;
import io.beam.ir.erlang.Module;
import software.amazon.smithy.model.shapes.ServiceShape;

final class ErlangCodecEmission {
  private ErlangCodecEmission() {}

  static void writeModule(ErlangContext ctx, String relativeFile, Module module) {
    writeModule(ctx, relativeFile, ErlangRenderer.render(module));
  }

  static void writeModule(ErlangContext ctx, String relativeFile, String erlangSource) {
    ctx.writerDelegator()
        .useFileWriter(
            relativeFile,
            writer -> {
              writer.pushGeneratedDocumentationSection();
              writer.write("$L", erlangSource);
            });
  }

  static void emitRuntimeHelpersIfNeeded(ErlangContext ctx, ServiceShape service, boolean server) {
    if (server) {
      ErlangRuntimeHelpersEmitter.emitIfNeeded(ctx, service);
    }
  }
}
