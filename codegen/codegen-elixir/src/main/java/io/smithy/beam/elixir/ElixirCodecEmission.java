package io.smithy.beam.elixir;

import io.beam.dsl.elixir.ElixirRenderer;
import io.beam.dsl.elixir.Module;

final class ElixirCodecEmission {
  private ElixirCodecEmission() {}

  static void writeModule(ElixirContext ctx, String relativeFile, Module module) {
    writeModule(ctx, relativeFile, ElixirRenderer.render(module));
  }

  static void writeModule(ElixirContext ctx, String relativeFile, String elixirSource) {
    ctx.writerDelegator()
        .useFileWriter(
            relativeFile,
            writer -> {
              writer.pushGeneratedDocumentationSection();
              writer.write("$L", elixirSource);
            });
  }
}
