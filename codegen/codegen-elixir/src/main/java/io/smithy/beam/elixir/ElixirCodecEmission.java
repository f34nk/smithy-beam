package io.smithy.beam.elixir;

import io.beam.ir.elixir.ElixirRenderer;
import io.beam.ir.elixir.Module;
import io.smithy.beam.ir.elixir.ExModule;

final class ElixirCodecEmission {
  private ElixirCodecEmission() {}

  static void writeModule(ElixirContext ctx, String relativeFile, Module module) {
    writeModule(ctx, relativeFile, ElixirRenderer.render(module));
  }

  static void writeModule(ElixirContext ctx, String relativeFile, ExModule module) {
    writeModule(ctx, relativeFile, module.asString());
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
