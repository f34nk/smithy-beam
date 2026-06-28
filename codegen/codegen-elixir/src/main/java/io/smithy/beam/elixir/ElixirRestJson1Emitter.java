package io.smithy.beam.elixir;

import io.smithy.beam.core.BeamElixirLayout;
import io.smithy.beam.ir.elixir.ExFunction;
import java.util.List;
import software.amazon.smithy.codegen.core.SymbolProvider;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.knowledge.HttpBindingIndex;
import software.amazon.smithy.model.shapes.OperationShape;
import software.amazon.smithy.model.shapes.ServiceShape;
import software.amazon.smithy.model.shapes.ShapeId;

/**
 * REST JSON 1 codec emitter for Elixir. Generates encode_request and decode_response functions per
 * operation using Jason for JSON and Req for HTTP.
 */
public final class ElixirRestJson1Emitter {

  private ElixirRestJson1Emitter() {}

  public static void emitServerCodecModule(ElixirContext ctx, ServiceShape service) {
    Model model = ctx.model();
    ShapeId protocol = ctx.resolvedProtocolTraitId();
    BeamElixirLayout layout =
        new BeamElixirLayout(ctx.settings(), service.getId().getNamespace(), service);
    ElixirRuntimeHelpersEmitter.emitIfNeeded(ctx, service);
    HttpBindingIndex httpIndex = HttpBindingIndex.of(model);
    SymbolProvider sp = ctx.symbolProvider();
    List<OperationShape> operations = ElixirTopDown.containedOperationsSorted(model, service);
    String serverCodecModule =
        ElixirSymbolProvider.toModuleName(layout.serverCodecModuleName(protocol));
    String runtimeMod = ElixirSymbolProvider.toModuleName(layout.runtimeTypesModuleName());
    String typesMod = ElixirSymbolProvider.toModuleName(layout.typesModuleName());
    String eventStreamModule = ElixirSymbolProvider.toModuleName(layout.eventStreamModuleName());

    ctx.writerDelegator()
        .useFileWriter(
            layout.serverCodecModuleName(protocol) + ".ex",
            writer -> {
              writer.write("defmodule $L do", serverCodecModule);
              writer.indent();
              writer.write(
                  "@moduledoc \"Server REST JSON 1 codecs for $L (generated). Do not edit.\"",
                  service.getId());
              writer.write("alias $L, as: RuntimeTypes", runtimeMod);
              writer.write("alias $L, as: Types", typesMod);
              writer.write("");
              writeCodecFunctions(
                  writer,
                  ElixirRestJsonIr.serverCodecFunctions(
                      model,
                      service,
                      operations,
                      httpIndex,
                      sp,
                      typesMod,
                      runtimeMod,
                      eventStreamModule));
              writer.dedent();
              writer.write("end");
            });
  }

  public static void emitCodecModule(ElixirContext ctx, ServiceShape service) {
    Model model = ctx.model();
    ShapeId protocol = ctx.resolvedProtocolTraitId();
    BeamElixirLayout layout =
        new BeamElixirLayout(ctx.settings(), service.getId().getNamespace(), service);
    HttpBindingIndex httpIndex = HttpBindingIndex.of(model);
    SymbolProvider sp = ctx.symbolProvider();
    String moduleName = ElixirSymbolProvider.toModuleName(layout.clientCodecModuleName(protocol));
    String codecFile = layout.clientCodecModuleName(protocol) + ".ex";
    String runtimeMod = ElixirSymbolProvider.toModuleName(layout.runtimeTypesModuleName());
    String typesMod = ElixirSymbolProvider.toModuleName(layout.typesModuleName());
    String eventStreamModule = ElixirSymbolProvider.toModuleName(layout.eventStreamModuleName());
    List<OperationShape> operations = ElixirTopDown.containedOperationsSorted(model, service);
    boolean encodeWithConfig = serviceHasHostLabelOperations(model, service);

    ctx.writerDelegator()
        .useFileWriter(
            codecFile,
            writer -> {
              writer.write("defmodule $L do", moduleName);
              writer.indent();
              writer.write(
                  "@moduledoc \"REST JSON 1 codecs for $L (generated). Do not edit.\"",
                  service.getId());
              writer.write("alias $L, as: RuntimeTypes", runtimeMod);
              writer.write("alias $L, as: Types", typesMod);
              writer.write("");
              writeCodecFunctions(
                  writer,
                  ElixirRestJsonIr.clientCodecFunctions(
                      model,
                      service,
                      operations,
                      httpIndex,
                      sp,
                      typesMod,
                      runtimeMod,
                      eventStreamModule,
                      encodeWithConfig));
              writer.dedent();
              writer.write("end");
            });
  }

  public static boolean serviceHasHostLabelOperations(Model model, ServiceShape service) {
    return ElixirRestJsonSupport.serviceHasHostLabelOperations(model, service);
  }

  private static void writeCodecFunctions(ElixirWriter writer, List<ExFunction> functions) {
    for (ExFunction function : functions) {
      writer.write("$L", function.asString());
      writer.write("");
    }
  }
}
