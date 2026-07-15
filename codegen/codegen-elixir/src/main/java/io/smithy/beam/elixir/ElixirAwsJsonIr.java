package io.smithy.beam.elixir;

import io.beam.dsl.elixir.Alias;
import io.beam.dsl.elixir.Function;
import io.beam.dsl.elixir.Module;
import io.beam.dsl.elixir.Moduledoc;
import io.smithy.beam.core.BeamAwsServiceMetadata;
import io.smithy.beam.core.BeamElixirLayout;
import io.smithy.beam.core.BeamProtocolIds;
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

  static Module buildClientCodecModule(ElixirContext ctx, ServiceShape service, ShapeId protocol) {
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
    List<Function> functions =
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

    return Module.of(
        moduleName,
        Moduledoc.of(
            "AWS JSON "
                + versionLabel
                + " codecs for "
                + service.getId()
                + " (generated). Do not edit."),
        List.of(),
        List.of(Alias.of(runtimeMod, "RuntimeTypes"), Alias.of(typesMod, "Types")),
        List.of(),
        List.of(),
        List.of(),
        List.of(),
        functions);
  }

  static Module buildServerCodecModule(ElixirContext ctx, ServiceShape service, ShapeId protocol) {
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
    List<Function> functions =
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

    return Module.of(
        moduleName,
        Moduledoc.of(
            "Server AWS JSON " + versionLabel + " codecs for " + service.getId() + " (generated)."),
        List.of(),
        List.of(Alias.of(runtimeMod, "RuntimeTypes"), Alias.of(typesMod, "Types")),
        List.of(),
        List.of(),
        List.of(),
        List.of(),
        functions);
  }

  static void emitClientCodecModule(ElixirContext ctx, ServiceShape service, ShapeId protocol) {
    ElixirCodecEmission.writeModule(
        ctx,
        clientCodecFileName(ctx, service, protocol),
        buildClientCodecModule(ctx, service, protocol));
  }

  static void emitServerCodecModule(ElixirContext ctx, ServiceShape service, ShapeId protocol) {
    ElixirCodecEmission.writeModule(
        ctx,
        serverCodecFileName(ctx, service, protocol),
        buildServerCodecModule(ctx, service, protocol));
  }

  static String contentType(ShapeId protocol) {
    String contentType = CONTENT_TYPES.get(protocol);
    if (contentType == null) {
      throw new IllegalArgumentException("Unsupported AWS JSON protocol: " + protocol);
    }
    return contentType;
  }

  static List<Function> sharedCodecHelpers(Model model, ServiceShape service, SymbolProvider sp) {
    return ElixirRestJsonIr.sharedCodecHelpers(model, service, sp);
  }

  static List<Function> clientOperationCodecFunctions(
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
    List<Function> functions = new ArrayList<>();
    for (OperationShape op : operations) {
      functions.addAll(
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
      functions.addAll(
          ElixirAwsJsonOperationIr.buildDecodeResponse(
              model, op, httpIndex, sp, typesMod, runtimeMod, eventStreamModule));
    }
    return functions;
  }

  static List<Function> clientCodecFunctions(
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
    List<Function> functions = new ArrayList<>();
    functions.addAll(
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
            eventStreamModule));
    for (OperationShape op : operations) {
      functions.addAll(ElixirAwsJsonOperationIr.buildErrorDispatch(model, op, sp, typesMod));
    }
    functions.addAll(codecHelperFunctions(model, service, sp));
    return functions;
  }

  private static List<Function> codecHelperFunctions(
      Model model, ServiceShape service, SymbolProvider sp) {
    List<Function> helpers = new ArrayList<>();
    helpers.addAll(ElixirRestJsonIr.structureHelperFunctions(model, service, sp));
    helpers.addAll(ElixirRestJsonIr.enumHelperFunctions(model, service, sp));
    helpers.addAll(ElixirRestJsonIr.unionHelperFunctions(model, service, sp));
    helpers.addAll(ElixirRestJsonIr.mapHelperFunctions(model, service, sp));
    helpers.addAll(ElixirRestJsonIr.privateCodecHelpers(model, service));
    return helpers;
  }

  static List<Function> serverCodecFunctions(
      Model model,
      ServiceShape service,
      List<OperationShape> operations,
      HttpBindingIndex httpIndex,
      SymbolProvider sp,
      String typesMod,
      String runtimeMod,
      String contentType,
      String eventStreamModule) {
    List<Function> functions = new ArrayList<>();
    for (OperationShape op : operations) {
      functions.addAll(
          ElixirAwsJsonOperationIr.buildDecodeRequest(
              model, op, httpIndex, sp, typesMod, runtimeMod, eventStreamModule));
      functions.addAll(
          ElixirAwsJsonOperationIr.buildEncodeResponse(
              model, op, httpIndex, sp, typesMod, runtimeMod, contentType, eventStreamModule));
    }
    functions.addAll(codecHelperFunctions(model, service, sp));
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
