package io.smithy.beam.erlang;

import io.beam.dsl.erlang.ErlangRenderer;
import io.beam.dsl.erlang.Module;

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
}
