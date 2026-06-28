package io.smithy.beam.elixir;

import io.smithy.beam.core.BeamElixirLayout;
import io.smithy.beam.core.BeamHostLabelIndex;
import io.smithy.beam.core.BeamProtocolIds;
import io.smithy.beam.core.BeamS3CustomizationIndex;
import io.smithy.beam.core.BeamXmlBindingIndex;
import io.smithy.beam.ir.elixir.ExFunction;
import java.util.List;
import software.amazon.smithy.codegen.core.SymbolProvider;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.knowledge.HttpBindingIndex;
import software.amazon.smithy.model.shapes.OperationShape;
import software.amazon.smithy.model.shapes.ServiceShape;
import software.amazon.smithy.model.traits.EndpointTrait;

/** REST-XML protocol codec emitter for Elixir clients and servers. */
public final class ElixirRestXmlEmitter {

  private ElixirRestXmlEmitter() {}

  public static void emitCodecModule(ElixirContext ctx, ServiceShape service) {
    Model model = ctx.model();
    BeamElixirLayout layout =
        new BeamElixirLayout(ctx.settings(), service.getId().getNamespace(), service);
    HttpBindingIndex httpIndex = HttpBindingIndex.of(model);
    SymbolProvider sp = ctx.symbolProvider();
    String moduleName =
        ElixirSymbolProvider.toModuleName(layout.clientCodecModuleName(BeamProtocolIds.REST_XML));
    String codecFile = layout.clientCodecModuleName(BeamProtocolIds.REST_XML) + ".ex";
    String runtimeMod = ElixirSymbolProvider.toModuleName(layout.runtimeTypesModuleName());
    String typesMod = ElixirSymbolProvider.toModuleName(layout.typesModuleName());
    List<OperationShape> operations = ElixirTopDown.containedOperationsSorted(model, service);
    boolean encodeWithConfig = serviceEncodesWithConfig(model, service);

    ctx.writerDelegator()
        .useFileWriter(
            codecFile,
            writer -> {
              writer.write("defmodule $L do", moduleName);
              writer.indent();
              writer.write(
                  "@moduledoc \"REST-XML codecs for $L (generated). Do not edit.\"",
                  service.getId());
              writer.write("alias $L, as: RuntimeTypes", runtimeMod);
              writer.write("alias $L, as: Types", typesMod);
              if (BeamS3CustomizationIndex.isS3Service(service)) {
                writer.write("alias S3Endpoint");
              }
              writer.write("");
              writeCodecFunctions(
                  writer,
                  ElixirRestXmlIr.clientCodecFunctions(
                      model,
                      service,
                      operations,
                      httpIndex,
                      sp,
                      typesMod,
                      runtimeMod,
                      BeamXmlBindingIndex.xmlNamespaceUri(service),
                      encodeWithConfig));
              writer.dedent();
              writer.write("end");
            });
  }

  public static void emitServerCodecModule(ElixirContext ctx, ServiceShape service) {
    Model model = ctx.model();
    BeamElixirLayout layout =
        new BeamElixirLayout(ctx.settings(), service.getId().getNamespace(), service);
    ElixirRuntimeHelpersEmitter.emitIfNeeded(ctx, service);
    HttpBindingIndex httpIndex = HttpBindingIndex.of(model);
    SymbolProvider sp = ctx.symbolProvider();
    String serverCodecModule =
        ElixirSymbolProvider.toModuleName(layout.serverCodecModuleName(BeamProtocolIds.REST_XML));
    String runtimeMod = ElixirSymbolProvider.toModuleName(layout.runtimeTypesModuleName());
    String typesMod = ElixirSymbolProvider.toModuleName(layout.typesModuleName());
    List<OperationShape> operations = ElixirTopDown.containedOperationsSorted(model, service);

    ctx.writerDelegator()
        .useFileWriter(
            layout.serverCodecModuleName(BeamProtocolIds.REST_XML) + ".ex",
            writer -> {
              writer.write("defmodule $L do", serverCodecModule);
              writer.indent();
              writer.write(
                  "@moduledoc \"Server REST-XML codecs for $L (generated). Do not edit.\"",
                  service.getId());
              writer.write("alias $L, as: RuntimeTypes", runtimeMod);
              writer.write("alias $L, as: Types", typesMod);
              writer.write("");
              writeCodecFunctions(
                  writer,
                  ElixirRestXmlIr.serverCodecFunctions(
                      model,
                      service,
                      operations,
                      httpIndex,
                      sp,
                      typesMod,
                      runtimeMod,
                      BeamXmlBindingIndex.xmlNamespaceUri(service)));
              writer.dedent();
              writer.write("end");
            });
  }

  public static boolean serviceHasHostLabelOperations(Model model, ServiceShape service) {
    BeamHostLabelIndex hostLabelIndex = BeamHostLabelIndex.of(model);
    for (OperationShape op : ElixirTopDown.containedOperationsSorted(model, service)) {
      if (!hostLabelIndex.hostLabelMembers(op).isEmpty() && op.hasTrait(EndpointTrait.class)) {
        return true;
      }
    }
    return false;
  }

  public static boolean serviceEncodesWithConfig(Model model, ServiceShape service) {
    return ElixirRestXmlIr.serviceEncodesWithConfig(model, service);
  }

  private static void writeCodecFunctions(ElixirWriter writer, List<ExFunction> functions) {
    for (ExFunction function : functions) {
      writer.write("$L", function.asString());
      writer.write("");
    }
  }
}
