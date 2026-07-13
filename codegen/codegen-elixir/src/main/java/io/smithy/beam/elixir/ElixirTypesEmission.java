package io.smithy.beam.elixir;

import io.smithy.beam.core.BeamElixirLayout;
import io.smithy.beam.ir.elixir.ExFunction;
import io.smithy.beam.ir.elixir.ExModuleEntry;
import io.smithy.beam.ir.elixir.ExNestedModule;
import io.smithy.beam.ir.elixir.ExTypesModule;
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
    List<ExModuleEntry> rootEntries = new ArrayList<>();
    List<ExNestedModule> splitModules = new ArrayList<>();

    for (ExModuleEntry entry : ctx.typesEntries()) {
      if (entry instanceof ExNestedModule nested) {
        if (shouldSplitNestedModule(nested, defstructSplitThreshold, enumSplitThreshold)) {
          splitModules.add(nested);
        } else {
          rootEntries.add(entry);
        }
      } else {
        rootEntries.add(entry);
      }
    }

    if (splitModules.isEmpty()) {
      ExTypesModule module =
          ExTypesModule.typesModule(
              ctx.moduleName(),
              ctx.typesPreambleEntries(),
              rootEntries,
              ctx.typesFunctions());
      writeTypesFile(ctx, ctx.definitionFile(), module);
      return;
    }

    ExTypesModule rootModule =
        ExTypesModule.typesModule(
            ctx.moduleName(), ctx.typesPreambleEntries(), rootEntries, ctx.typesFunctions());
    writeTypesFile(ctx, ctx.definitionFile(), rootModule);

    BeamElixirLayout layout =
        new BeamElixirLayout(ctx.settings(), ctx.service().getId().getNamespace(), ctx.service());
    for (ExNestedModule nested : splitModules) {
      String file = layout.nestedTypeModuleFile(nested.name());
      ExTypesModule topLevel = nested.asTopLevelModule(ctx.moduleName());
      writeTypesFile(ctx, file, topLevel);
    }
  }

  private static boolean shouldSplitNestedModule(
      ExNestedModule nested, int defstructSplitThreshold, int enumSplitThreshold) {
    return nested.defstructLiteralSizeEstimate() > defstructSplitThreshold
        || nested.enumModuleSizeEstimate() > enumSplitThreshold;
  }

  private static void writeTypesFile(ElixirContext ctx, String file, ExTypesModule module) {
    writeTypesSource(ctx, file, module.asString());
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
