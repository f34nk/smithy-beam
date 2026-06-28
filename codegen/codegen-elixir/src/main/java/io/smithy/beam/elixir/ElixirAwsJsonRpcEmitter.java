package io.smithy.beam.elixir;

import io.smithy.beam.core.BeamAwsServiceMetadata;
import io.smithy.beam.core.BeamElixirLayout;
import io.smithy.beam.ir.elixir.ExFunction;
import java.util.ArrayList;
import java.util.List;
import software.amazon.smithy.codegen.core.SymbolProvider;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.knowledge.HttpBindingIndex;
import software.amazon.smithy.model.shapes.MemberShape;
import software.amazon.smithy.model.shapes.OperationShape;
import software.amazon.smithy.model.shapes.ServiceShape;
import software.amazon.smithy.model.shapes.ShapeId;
import software.amazon.smithy.model.shapes.StructureShape;
import software.amazon.smithy.model.traits.HttpErrorTrait;

/** Shared AWS JSON RPC codec emitter for Elixir. */
final class ElixirAwsJsonRpcEmitter {

  private ElixirAwsJsonRpcEmitter() {}

  static void emitServerCodecModule(
      ElixirContext ctx,
      ServiceShape service,
      ShapeId protocol,
      String contentType,
      String versionLabel) {
    Model model = ctx.model();
    BeamElixirLayout layout =
        new BeamElixirLayout(ctx.settings(), service.getId().getNamespace(), service);
    String serverCodecFile = layout.serverCodecModuleName(protocol) + ".ex";
    String serverCodecModule =
        ElixirSymbolProvider.toModuleName(layout.serverCodecModuleName(protocol));
    ElixirRuntimeHelpersEmitter.emitIfNeeded(ctx, service);
    HttpBindingIndex httpIndex = HttpBindingIndex.of(model);
    SymbolProvider sp = ctx.symbolProvider();
    String runtimeMod = ElixirSymbolProvider.toModuleName(layout.runtimeTypesModuleName());
    String typesMod = ElixirSymbolProvider.toModuleName(layout.typesModuleName());
    String eventStreamMod = ElixirSymbolProvider.toModuleName(layout.eventStreamModuleName());
    List<OperationShape> operations = ElixirTopDown.containedOperationsSorted(model, service);

    ctx.writerDelegator()
        .useFileWriter(
            serverCodecFile,
            writer -> {
              writer.write("defmodule $L do", serverCodecModule);
              writer.indent();
              writer.write(
                  "@moduledoc \"Server AWS JSON $L codecs for $L (generated).\"",
                  versionLabel,
                  service.getId());
              writer.write("alias $L, as: RuntimeTypes", runtimeMod);
              writer.write("alias $L, as: Types", typesMod);
              writer.write("");
              writeCodecFunctions(
                  writer,
                  ElixirAwsJsonIr.serverCodecFunctions(
                      model,
                      service,
                      operations,
                      httpIndex,
                      sp,
                      typesMod,
                      runtimeMod,
                      contentType,
                      eventStreamMod));
              writer.dedent();
              writer.write("end");
            });
  }

  static void emitCodecModule(
      ElixirContext ctx,
      ServiceShape service,
      ShapeId protocol,
      String contentType,
      String versionLabel) {
    BeamAwsServiceMetadata.from(service).orElseThrow();
    Model model = ctx.model();
    BeamElixirLayout layout =
        new BeamElixirLayout(ctx.settings(), service.getId().getNamespace(), service);
    HttpBindingIndex httpIndex = HttpBindingIndex.of(model);
    SymbolProvider sp = ctx.symbolProvider();
    String moduleName = ElixirSymbolProvider.toModuleName(layout.clientCodecModuleName(protocol));
    String codecFile = layout.clientCodecModuleName(protocol) + ".ex";
    String runtimeMod = ElixirSymbolProvider.toModuleName(layout.runtimeTypesModuleName());
    String typesMod = ElixirSymbolProvider.toModuleName(layout.typesModuleName());
    String eventStreamMod = ElixirSymbolProvider.toModuleName(layout.eventStreamModuleName());
    String targetPrefix = service.getId().getName();
    List<OperationShape> operations = ElixirTopDown.containedOperationsSorted(model, service);

    ctx.writerDelegator()
        .useFileWriter(
            codecFile,
            writer -> {
              writer.write("defmodule $L do", moduleName);
              writer.indent();
              writer.write(
                  "@moduledoc \"AWS JSON $L codecs for $L (generated). Do not edit.\"",
                  versionLabel,
                  service.getId());
              writer.write("alias $L, as: RuntimeTypes", runtimeMod);
              writer.write("alias $L, as: Types", typesMod);
              writer.write("");
              writeCodecFunctions(
                  writer,
                  ElixirAwsJsonIr.clientOperationCodecFunctions(
                      model,
                      service,
                      operations,
                      httpIndex,
                      sp,
                      typesMod,
                      runtimeMod,
                      targetPrefix,
                      contentType,
                      eventStreamMod));
              for (OperationShape op : operations) {
                writeErrorDispatch(writer, model, op, sp, typesMod);
              }
              writeCodecFunctions(
                  writer, ElixirAwsJsonIr.sharedCodecHelpers(model, service, sp));
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

  private static void writeErrorDispatch(
      ElixirWriter writer, Model model, OperationShape op, SymbolProvider sp, String typesMod) {

    String opName = sp.toSymbol(op).getName();
    List<ShapeId> errors = new ArrayList<>(op.getErrors());

    writer.write("# Error dispatch for $L", op.getId());
    for (ShapeId errorId : errors) {
      StructureShape errShape = model.expectShape(errorId, StructureShape.class);
      String modName = sp.toSymbol(errShape).getName();
      int httpStatus =
          errShape.hasTrait(HttpErrorTrait.class)
              ? errShape.expectTrait(HttpErrorTrait.class).getCode()
              : -1;
      if (httpStatus <= 0) {
        continue;
      }
      writer.write("defp decode_$L_response_error($L, _headers, body) do", opName, httpStatus);
      writer.indent();
      writer.write("decoded = decode_json_body(body)");
      List<String> fields = buildErrorFields(errShape);
      writer.write("{:error, struct!($L.$L, %{$L})}", typesMod, modName, String.join(", ", fields));
      writer.dedent();
      writer.write("end");
      writer.write("");
    }

    boolean hasTypeDiscriminated =
        errors.stream()
            .anyMatch(
                e -> !model.expectShape(e, StructureShape.class).hasTrait(HttpErrorTrait.class));

    if (hasTypeDiscriminated) {
      writer.write(
          "defp decode_$L_response_error(status, _headers, body) when status >= 400 do", opName);
      writer.indent();
      writer.write("decoded = decode_json_body(body)");
      writer.write("error_type = Map.get(decoded, \"__type\")");
      writer.write("case error_type do");
      writer.indent();
      for (ShapeId errorId : errors) {
        StructureShape errShape = model.expectShape(errorId, StructureShape.class);
        if (errShape.hasTrait(HttpErrorTrait.class)) {
          continue;
        }
        String modName = sp.toSymbol(errShape).getName();
        String localName = errorId.getName();
        List<String> fields = buildErrorFields(errShape);
        writer.write("\"$L\" ->", localName);
        writer.indent();
        writer.write(
            "{:error, struct!($L.$L, %{$L})}", typesMod, modName, String.join(", ", fields));
        writer.dedent();
      }
      writer.write("");
      writer.write("_ -> {:error, {:unknown_error, status, body}}");
      writer.dedent();
      writer.write("end");
      writer.dedent();
      writer.write("end");
      writer.write("");
    } else {
      writer.write("defp decode_$L_response_error(status, _headers, body) do", opName);
      writer.indent();
      writer.write("{:error, {:unknown_error, status, body}}");
      writer.dedent();
      writer.write("end");
      writer.write("");
    }
  }

  private static List<String> buildErrorFields(StructureShape errShape) {
    List<String> fields = new ArrayList<>();
    for (MemberShape member : errShape.members()) {
      if (member.getMemberName().equals("__beam_error_kind")) {
        continue;
      }
      String field = io.smithy.beam.core.BeamNameUtils.toSnakeCase(member.getMemberName());
      fields.add(field + ": Map.get(decoded, \"" + member.getMemberName() + "\")");
    }
    return fields;
  }
}
