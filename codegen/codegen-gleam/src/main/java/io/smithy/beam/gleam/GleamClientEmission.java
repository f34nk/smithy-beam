package io.smithy.beam.gleam;

import io.beam.lang.gleam.GleamRenderer;
import io.beam.lang.gleam.Module;

final class GleamClientEmission {

  private GleamClientEmission() {}

  static void writeModule(GleamContext ctx, String file, Module module) {
    String source = GleamRenderer.render(module);
    ctx.writerDelegator()
        .useFileWriter(file, writer -> writer.writeInline("$L", source));
  }
}
