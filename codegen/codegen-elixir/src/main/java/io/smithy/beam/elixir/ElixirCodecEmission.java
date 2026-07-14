package io.smithy.beam.elixir;

import io.beam.ir.elixir.ElixirRenderer;
import io.beam.ir.elixir.Function;
import io.beam.ir.elixir.Module;
import io.smithy.beam.ir.elixir.ExFunction;
import io.smithy.beam.ir.elixir.ExModule;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

final class ElixirCodecEmission {
  private ElixirCodecEmission() {}

  static void writeModule(ElixirContext ctx, String relativeFile, Module module) {
    writeModule(ctx, relativeFile, ElixirRenderer.render(module));
  }

  static void writeModule(
      ElixirContext ctx, String relativeFile, Module module, List<ExFunction> legacyFunctions) {
    writeModule(ctx, relativeFile, module, legacyFunctions, List.of());
  }

  static void writeModule(
      ElixirContext ctx,
      String relativeFile,
      Module module,
      List<ExFunction> legacyFunctions,
      List<Function> beamIrFunctions) {
    String rendered = ElixirRenderer.render(module);
    List<String> spliced = new ArrayList<>();
    if (!beamIrFunctions.isEmpty()) {
      spliced.add(
          beamIrFunctions.stream()
              .map(ElixirRenderer::renderFunction)
              .collect(Collectors.joining("\n\n")));
    }
    if (!legacyFunctions.isEmpty()) {
      spliced.add(
          legacyFunctions.stream().map(ExFunction::asString).collect(Collectors.joining("\n\n")));
    }
    if (!spliced.isEmpty()) {
      String legacy = String.join("\n\n", spliced);
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
