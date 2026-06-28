package io.smithy.beam.elixir;

import io.smithy.beam.core.BeamAwsServiceMetadata;
import io.smithy.beam.core.BeamElixirLayout;
import io.smithy.beam.core.BeamProtocolIds;
import io.smithy.beam.core.BeamXmlBindingIndex;
import io.smithy.beam.ir.elixir.ExFunction;
import java.util.List;
import software.amazon.smithy.codegen.core.SymbolProvider;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.knowledge.HttpBindingIndex;
import software.amazon.smithy.model.shapes.OperationShape;
import software.amazon.smithy.model.shapes.ServiceShape;
import software.amazon.smithy.model.shapes.ShapeId;

/** AWS Query protocol codec emitter for Elixir clients. */
public final class ElixirAwsQueryEmitter {

  private ElixirAwsQueryEmitter() {}

  static void emitCodecModule(ElixirContext ctx, ServiceShape service) {
    emitCodecModule(ctx, service, BeamProtocolIds.AWS_QUERY);
  }

  static void emitCodecModule(ElixirContext ctx, ServiceShape service, ShapeId protocolTraitId) {
    boolean ec2Query = BeamProtocolIds.EC2_QUERY.equals(protocolTraitId);
    BeamAwsServiceMetadata.from(service).orElseThrow();
    Model model = ctx.model();
    BeamElixirLayout layout =
        new BeamElixirLayout(ctx.settings(), service.getId().getNamespace(), service);
    HttpBindingIndex httpIndex = HttpBindingIndex.of(model);
    SymbolProvider sp = ctx.symbolProvider();
    String moduleName =
        ElixirSymbolProvider.toModuleName(layout.clientCodecModuleName(protocolTraitId));
    String codecFile = layout.clientCodecModuleName(protocolTraitId) + ".ex";
    String runtimeMod = ElixirSymbolProvider.toModuleName(layout.runtimeTypesModuleName());
    String typesMod = ElixirSymbolProvider.toModuleName(layout.typesModuleName());
    List<OperationShape> operations = ElixirTopDown.containedOperationsSorted(model, service);

    ctx.writerDelegator()
        .useFileWriter(
            codecFile,
            writer -> {
              writer.write("defmodule $L do", moduleName);
              writer.indent();
              writer.write(
                  "@moduledoc \"AWS Query codecs for $L (generated). Do not edit.\"",
                  service.getId());
              writer.write("alias $L, as: RuntimeTypes", runtimeMod);
              writer.write("alias $L, as: Types", typesMod);
              writer.write("");
              writeCodecFunctions(
                  writer,
                  ElixirAwsQueryIr.clientCodecFunctions(
                      model, service, operations, httpIndex, sp, typesMod, runtimeMod, ec2Query));
              writer.dedent();
              writer.write("end");
            });
  }

  static void emitServerCodecModule(ElixirContext ctx, ServiceShape service) {
    emitServerCodecModule(ctx, service, BeamProtocolIds.AWS_QUERY);
  }

  static void emitServerCodecModule(
      ElixirContext ctx, ServiceShape service, ShapeId protocolTraitId) {
    boolean ec2Query = BeamProtocolIds.EC2_QUERY.equals(protocolTraitId);
    Model model = ctx.model();
    BeamElixirLayout layout =
        new BeamElixirLayout(ctx.settings(), service.getId().getNamespace(), service);
    SymbolProvider sp = ctx.symbolProvider();
    String serverCodecModule =
        ElixirSymbolProvider.toModuleName(layout.serverCodecModuleName(protocolTraitId));
    String runtimeMod = ElixirSymbolProvider.toModuleName(layout.runtimeTypesModuleName());
    String typesMod = ElixirSymbolProvider.toModuleName(layout.typesModuleName());
    List<OperationShape> operations = ElixirTopDown.containedOperationsSorted(model, service);

    ctx.writerDelegator()
        .useFileWriter(
            layout.serverCodecModuleName(protocolTraitId) + ".ex",
            writer -> {
              writer.write("defmodule $L do", serverCodecModule);
              writer.indent();
              writer.write(
                  "@moduledoc \"Server AWS Query codecs for $L (generated). Do not edit.\"",
                  service.getId());
              writer.write("alias $L, as: RuntimeTypes", runtimeMod);
              writer.write("alias $L, as: Types", typesMod);
              writer.write("");
              writeCodecFunctions(
                  writer,
                  ElixirAwsQueryIr.serverCodecFunctions(
                      model,
                      service,
                      operations,
                      sp,
                      typesMod,
                      runtimeMod,
                      BeamXmlBindingIndex.xmlNamespaceUri(service),
                      ec2Query));
              writer.dedent();
              writer.write("end");
            });
  }

  private static void writeCodecFunctions(ElixirWriter writer, List<ExFunction> functions) {
    for (ExFunction function : functions) {
      writer.write("$L", function.asString());
      writer.write("");
    }
  }
}
