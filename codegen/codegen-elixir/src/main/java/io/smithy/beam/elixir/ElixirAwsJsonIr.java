package io.smithy.beam.elixir;

import io.smithy.beam.core.BeamAwsServiceMetadata;
import io.smithy.beam.core.BeamElixirLayout;
import io.smithy.beam.core.BeamProtocolIds;
import io.smithy.beam.ir.elixir.ExAliasAttr;
import io.smithy.beam.ir.elixir.ExFunction;
import io.smithy.beam.ir.elixir.ExModuledoc;
import io.smithy.beam.ir.elixir.ExModule;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import software.amazon.smithy.codegen.core.SymbolProvider;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.knowledge.HttpBindingIndex;
import software.amazon.smithy.model.shapes.OperationShape;
import software.amazon.smithy.model.shapes.ServiceShape;
import software.amazon.smithy.model.shapes.ShapeId;

final class ElixirAwsJsonIr {
  private static final Map<ShapeId, String> CONTENT_TYPES =
      Map.of(
          BeamProtocolIds.AWS_JSON_1_0, "application/x-amz-json-1.0",
          BeamProtocolIds.AWS_JSON_1_1, "application/x-amz-json-1.1");

  private static final Map<ShapeId, String> VERSION_LABELS =
      Map.of(
          BeamProtocolIds.AWS_JSON_1_0, "1.0",
          BeamProtocolIds.AWS_JSON_1_1, "1.1");

  private ElixirAwsJsonIr() {}

  static String clientCodecFileName(ElixirContext ctx, ServiceShape service, ShapeId protocol) {
    return layout(ctx, service).clientCodecModuleName(protocol) + ".ex";
  }

  static String serverCodecFileName(ElixirContext ctx, ServiceShape service, ShapeId protocol) {
    return layout(ctx, service).serverCodecModuleName(protocol) + ".ex";
  }

  static ExModule buildClientCodecModule(
      ElixirContext ctx, ServiceShape service, ShapeId protocol) {
    BeamAwsServiceMetadata.from(service).orElseThrow();
    Model model = ctx.model();
    BeamElixirLayout layout = layout(ctx, service);
    HttpBindingIndex httpIndex = HttpBindingIndex.of(model);
    SymbolProvider sp = ctx.symbolProvider();
    String moduleName = ElixirSymbolProvider.toModuleName(layout.clientCodecModuleName(protocol));
    String runtimeMod = ElixirSymbolProvider.toModuleName(layout.runtimeTypesModuleName());
    String typesMod = ElixirSymbolProvider.toModuleName(layout.typesModuleName());
    String eventStreamMod = ElixirSymbolProvider.toModuleName(layout.eventStreamModuleName());
    String targetPrefix = service.getId().getName();
    String contentType = contentType(protocol);
    String versionLabel = versionLabel(protocol);

    List<OperationShape> operations = ElixirTopDown.containedOperationsSorted(model, service);
    List<ExFunction> functions =
        clientCodecFunctions(
            model,
            service,
            operations,
            httpIndex,
            sp,
            typesMod,
            runtimeMod,
            targetPrefix,
            contentType,
            eventStreamMod);

    return ExModule.module(
        moduleName,
        List.of(
            ExModuledoc.moduledoc(
                "AWS JSON "
                    + versionLabel
                    + " codecs for "
                    + service.getId()
                    + " (generated). Do not edit.")),
        List.of(
            ExAliasAttr.alias(runtimeMod, "RuntimeTypes"), ExAliasAttr.alias(typesMod, "Types")),
        functions);
  }

  static ExModule buildServerCodecModule(
      ElixirContext ctx, ServiceShape service, ShapeId protocol) {
    Model model = ctx.model();
    BeamElixirLayout layout = layout(ctx, service);
    HttpBindingIndex httpIndex = HttpBindingIndex.of(model);
    SymbolProvider sp = ctx.symbolProvider();
    String moduleName = ElixirSymbolProvider.toModuleName(layout.serverCodecModuleName(protocol));
    String runtimeMod = ElixirSymbolProvider.toModuleName(layout.runtimeTypesModuleName());
    String typesMod = ElixirSymbolProvider.toModuleName(layout.typesModuleName());
    String eventStreamMod = ElixirSymbolProvider.toModuleName(layout.eventStreamModuleName());
    String contentType = contentType(protocol);
    String versionLabel = versionLabel(protocol);

    List<OperationShape> operations = ElixirTopDown.containedOperationsSorted(model, service);
    List<ExFunction> functions =
        serverCodecFunctions(
            model,
            service,
            operations,
            httpIndex,
            sp,
            typesMod,
            runtimeMod,
            contentType,
            eventStreamMod);

    return ExModule.module(
        moduleName,
        List.of(
            ExModuledoc.moduledoc(
                "Server AWS JSON "
                    + versionLabel
                    + " codecs for "
                    + service.getId()
                    + " (generated).")),
        List.of(
            ExAliasAttr.alias(runtimeMod, "RuntimeTypes"), ExAliasAttr.alias(typesMod, "Types")),
        functions);
  }

  static void emitClientCodecModule(ElixirContext ctx, ServiceShape service, ShapeId protocol) {
    ExModule module = buildClientCodecModule(ctx, service, protocol);
    ElixirCodecEmission.writeModule(ctx, clientCodecFileName(ctx, service, protocol), module);
  }

  static void emitServerCodecModule(ElixirContext ctx, ServiceShape service, ShapeId protocol) {
    ElixirCodecEmission.emitRuntimeHelpersIfNeeded(ctx, service, true);
    ExModule module = buildServerCodecModule(ctx, service, protocol);
    ElixirCodecEmission.writeModule(ctx, serverCodecFileName(ctx, service, protocol), module);
  }

  static String contentType(ShapeId protocol) {
    String contentType = CONTENT_TYPES.get(protocol);
    if (contentType == null) {
      throw new IllegalArgumentException("Unsupported AWS JSON protocol: " + protocol);
    }
    return contentType;
  }

  static List<ExFunction> sharedCodecHelpers(
      Model model, ServiceShape service, SymbolProvider sp) {
    return ElixirRestJsonIr.sharedCodecHelpers(model, service, sp);
  }

  static List<ExFunction> clientOperationCodecFunctions(
      Model model,
      ServiceShape service,
      List<OperationShape> operations,
      HttpBindingIndex httpIndex,
      SymbolProvider sp,
      String typesMod,
      String runtimeMod,
      String targetPrefix,
      String contentType,
      String eventStreamModule) {
    List<ExFunction> functions = new ArrayList<>();
    for (OperationShape op : operations) {
      functions.add(
          ElixirAwsJsonOperationIr.buildEncodeRequest(
              model,
              op,
              httpIndex,
              sp,
              typesMod,
              runtimeMod,
              targetPrefix,
              contentType,
              eventStreamModule));
      functions.add(
          ElixirAwsJsonOperationIr.buildDecodeResponse(
              model, op, httpIndex, sp, typesMod, runtimeMod, eventStreamModule));
    }
    return functions;
  }

  static List<ExFunction> clientCodecFunctions(
      Model model,
      ServiceShape service,
      List<OperationShape> operations,
      HttpBindingIndex httpIndex,
      SymbolProvider sp,
      String typesMod,
      String runtimeMod,
      String targetPrefix,
      String contentType,
      String eventStreamModule) {
    List<ExFunction> functions =
        clientOperationCodecFunctions(
            model,
            service,
            operations,
            httpIndex,
            sp,
            typesMod,
            runtimeMod,
            targetPrefix,
            contentType,
            eventStreamModule);
    for (OperationShape op : operations) {
      functions.add(ElixirAwsJsonOperationIr.buildErrorDispatch(model, op, sp, typesMod));
    }
    functions.addAll(sharedCodecHelpers(model, service, sp));
    return functions;
  }

  static List<ExFunction> serverCodecFunctions(
      Model model,
      ServiceShape service,
      List<OperationShape> operations,
      HttpBindingIndex httpIndex,
      SymbolProvider sp,
      String typesMod,
      String runtimeMod,
      String contentType,
      String eventStreamModule) {
    List<ExFunction> functions = new ArrayList<>();
    for (OperationShape op : operations) {
      functions.add(
          ElixirAwsJsonOperationIr.buildDecodeRequest(
              model, op, httpIndex, sp, typesMod, runtimeMod, eventStreamModule));
      functions.add(
          ElixirAwsJsonOperationIr.buildEncodeResponse(
              model,
              op,
              httpIndex,
              sp,
              typesMod,
              runtimeMod,
              contentType,
              eventStreamModule));
    }
    functions.addAll(sharedCodecHelpers(model, service, sp));
    return functions;
  }

  private static BeamElixirLayout layout(ElixirContext ctx, ServiceShape service) {
    return new BeamElixirLayout(ctx.settings(), service.getId().getNamespace(), service);
  }

  private static String versionLabel(ShapeId protocol) {
    String versionLabel = VERSION_LABELS.get(protocol);
    if (versionLabel == null) {
      throw new IllegalArgumentException("Unsupported AWS JSON protocol: " + protocol);
    }
    return versionLabel;
  }
}
