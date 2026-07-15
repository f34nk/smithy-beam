package io.smithy.beam.elixir;

import io.beam.dsl.elixir.ElixirRenderer;
import io.beam.dsl.elixir.Module;
import io.beam.dsl.elixir.Moduledoc;
import io.beam.dsl.elixir.TypesModule;
import io.smithy.beam.core.BeamElixirLayout;
import java.util.ArrayList;
import java.util.List;
import software.amazon.smithy.codegen.core.WriterDelegator;

final class ElixirTypesEmission {

  private ElixirTypesEmission() {}

  static void emit(ElixirContext ctx) {
    writeTypesModules(
        ctx,
        ctx.settings().typesDefstructSplitThreshold(),
        ctx.settings().typesEnumSplitThreshold());
  }

  static void writeTypesModules(
      ElixirContext ctx, int defstructSplitThreshold, int enumSplitThreshold) {
    Moduledoc moduledoc = null;
    List<ElixirTypesEntry> inlineEntries = new ArrayList<>();
    List<ElixirTypesEntry> splitEntries = new ArrayList<>();

    for (ElixirTypesEntry entry : ctx.typesEntries()) {
      if (entry instanceof ElixirTypesModuledocEntry moduledocEntry) {
        moduledoc = moduledocEntry.moduledoc();
        continue;
      }
      if (shouldSplitEntry(entry, defstructSplitThreshold, enumSplitThreshold)) {
        splitEntries.add(entry);
      } else {
        inlineEntries.add(entry);
      }
    }

    Module rootModule =
        ElixirBeamIrTypes.rootTypesModule(
            ctx.moduleName(), moduledoc, inlineEntries, ctx.typesFunctions());
    writeTypesFile(ctx, ctx.definitionFile(), rootModule);

    if (splitEntries.isEmpty()) {
      return;
    }

    BeamElixirLayout layout =
        new BeamElixirLayout(ctx.settings(), ctx.service().getId().getNamespace(), ctx.service());
    for (ElixirTypesEntry entry : splitEntries) {
      writeSplitTypesFile(ctx, layout, entry);
    }
  }

  private static boolean shouldSplitEntry(
      ElixirTypesEntry entry, int defstructSplitThreshold, int enumSplitThreshold) {
    return switch (entry) {
      case ElixirTypesStructNested(TypesModule typesModule) ->
          ElixirBeamIrTypes.shouldSplitStruct(typesModule, defstructSplitThreshold);
      case ElixirTypesEmbeddedNested embedded ->
          ElixirBeamIrTypes.shouldSplitEmbedded(embedded, enumSplitThreshold);
      default -> false;
    };
  }

  private static void writeSplitTypesFile(
      ElixirContext ctx, BeamElixirLayout layout, ElixirTypesEntry entry) {
    switch (entry) {
      case ElixirTypesStructNested(TypesModule typesModule) -> {
        String file = layout.nestedTypeModuleFile(typesModule.name());
        TypesModule topLevel = ElixirBeamIrTypes.splitStructModule(ctx.moduleName(), typesModule);
        writeTypesSource(ctx, file, ElixirRenderer.render(topLevel));
      }
      case ElixirTypesEmbeddedNested embedded -> {
        String file = layout.nestedTypeModuleFile(embedded.name());
        Module topLevel = ElixirBeamIrTypes.splitEmbeddedModule(ctx.moduleName(), embedded);
        writeTypesFile(ctx, file, topLevel);
      }
      default -> {}
    }
  }

  private static void writeTypesFile(ElixirContext ctx, String file, Module module) {
    writeTypesSource(ctx, file, ElixirRenderer.render(module));
  }

  private static void writeTypesSource(ElixirContext ctx, String file, String source) {
    WriterDelegator<ElixirWriter> delegator = ctx.writerDelegator();
    delegator.useFileWriter(
        file,
        writer -> {
          writer.pushGeneratedDocumentationSection();
          writer.write("$L", source);
          writer.popState();
        });
  }
}
