package io.smithy.beam.gleam;

import io.beam.lang.gleam.GleamRenderer;
import io.beam.lang.gleam.Module;

final class GleamTypesEmission {

  private GleamTypesEmission() {}

  static void emit(GleamContext ctx) {
    GleamContext.GleamTypesBuilder builder = ctx.typesBuilder();
    Module module =
        Module.of(
            ctx.moduleName(),
            builder.moduleDocOrNull(),
            builder.imports(),
            builder.typeAliases(),
            builder.customTypes());
    String source = GleamRenderer.render(module);
    ctx.writerDelegator()
        .useFileWriter(ctx.definitionFile(), writer -> writer.writeInline("$L", source));
  }
}
