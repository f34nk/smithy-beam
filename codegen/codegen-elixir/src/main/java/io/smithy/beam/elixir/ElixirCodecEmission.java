package io.smithy.beam.elixir;

import io.beam.ir.elixir.ElixirRenderer;
import io.beam.ir.elixir.Module;
import io.smithy.beam.ir.elixir.ExFunction;
import io.smithy.beam.ir.elixir.ExModule;
import java.util.List;
import java.util.stream.Collectors;

final class ElixirCodecEmission {
  private ElixirCodecEmission() {}

  static void writeModule(ElixirContext ctx, String relativeFile, Module module) {
    writeModule(ctx, relativeFile, ElixirRenderer.render(module));
  }

  static void writeModule(
      ElixirContext ctx, String relativeFile, Module module, List<ExFunction> legacyFunctions) {
    String rendered = ElixirRenderer.render(module);
    if (!legacyFunctions.isEmpty()) {
      String legacy =
          legacyFunctions.stream().map(ExFunction::asString).collect(Collectors.joining("\n\n"));
      int endIndex = rendered.lastIndexOf("\nend");
      rendered = rendered.substring(0, endIndex) + "\n\n" + legacy + rendered.substring(endIndex);
    }
    writeModule(ctx, relativeFile, rendered);
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
