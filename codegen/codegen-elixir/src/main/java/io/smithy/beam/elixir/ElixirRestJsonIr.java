package io.smithy.beam.elixir;

import io.beam.ir.elixir.Alias;
import io.beam.ir.elixir.Function;
import io.beam.ir.elixir.Module;
import io.beam.ir.elixir.Moduledoc;
import io.smithy.beam.core.BeamElixirLayout;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import software.amazon.smithy.codegen.core.SymbolProvider;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.knowledge.HttpBindingIndex;
import software.amazon.smithy.model.shapes.EnumShape;
import software.amazon.smithy.model.shapes.IntEnumShape;
import software.amazon.smithy.model.shapes.MapShape;
import software.amazon.smithy.model.shapes.OperationShape;
import software.amazon.smithy.model.shapes.ServiceShape;
import software.amazon.smithy.model.shapes.ShapeId;
import software.amazon.smithy.model.shapes.StructureShape;
import software.amazon.smithy.model.shapes.UnionShape;

final class ElixirRestJsonIr {
  private ElixirRestJsonIr() {}

  static String clientCodecFileName(ElixirContext ctx, ServiceShape service) {
    return layout(ctx, service).clientCodecModuleName(ctx.resolvedProtocolTraitId()) + ".ex";
  }

  static String serverCodecFileName(ElixirContext ctx, ServiceShape service) {
    return layout(ctx, service).serverCodecModuleName(ctx.resolvedProtocolTraitId()) + ".ex";
  }

  static Module buildClientCodecModule(ElixirContext ctx, ServiceShape service) {
    Model model = ctx.model();
    ShapeId protocol = ctx.resolvedProtocolTraitId();
    BeamElixirLayout layout = layout(ctx, service);
    HttpBindingIndex httpIndex = HttpBindingIndex.of(model);
    SymbolProvider sp = ctx.symbolProvider();
    String moduleName = ElixirSymbolProvider.toModuleName(layout.clientCodecModuleName(protocol));
    String runtimeMod = ElixirSymbolProvider.toModuleName(layout.runtimeTypesModuleName());
    String typesMod = ElixirSymbolProvider.toModuleName(layout.typesModuleName());
    String eventStreamModule = ElixirSymbolProvider.toModuleName(layout.eventStreamModuleName());
    List<OperationShape> operations = ElixirTopDown.containedOperationsSorted(model, service);
    boolean encodeWithConfig = ElixirRestJsonSupport.serviceHasHostLabelOperations(model, service);
    List<Function> functions =
        clientCodecFunctions(
            model,
            service,
            operations,
            httpIndex,
            sp,
            typesMod,
            runtimeMod,
            eventStreamModule,
            encodeWithConfig);

    return new Module(
        moduleName,
        Moduledoc.of(
            "REST JSON 1 codecs for " + service.getId() + " (generated). Do not edit."),
        List.of(),
        List.of(
            Alias.of(runtimeMod, "RuntimeTypes"),
            Alias.of(typesMod, "Types")),
        List.of(),
        List.of(),
        List.of(),
        List.of(),
        functions);
  }

  static Module buildServerCodecModule(ElixirContext ctx, ServiceShape service) {
    Model model = ctx.model();
    ShapeId protocol = ctx.resolvedProtocolTraitId();
    BeamElixirLayout layout = layout(ctx, service);
    HttpBindingIndex httpIndex = HttpBindingIndex.of(model);
    SymbolProvider sp = ctx.symbolProvider();
    String moduleName = ElixirSymbolProvider.toModuleName(layout.serverCodecModuleName(protocol));
    String runtimeMod = ElixirSymbolProvider.toModuleName(layout.runtimeTypesModuleName());
    String typesMod = ElixirSymbolProvider.toModuleName(layout.typesModuleName());
    String eventStreamModule = ElixirSymbolProvider.toModuleName(layout.eventStreamModuleName());
    List<OperationShape> operations = ElixirTopDown.containedOperationsSorted(model, service);
    List<Function> functions =
        serverCodecFunctions(
            model, service, operations, httpIndex, sp, typesMod, runtimeMod, eventStreamModule);

    return new Module(
        moduleName,
        Moduledoc.of(
            "Server REST JSON 1 codecs for " + service.getId() + " (generated). Do not edit."),
        List.of(),
        List.of(
            Alias.of(runtimeMod, "RuntimeTypes"),
            Alias.of(typesMod, "Types")),
        List.of(),
        List.of(),
        List.of(),
        List.of(),
        functions);
  }

  static void emitClientCodecModule(ElixirContext ctx, ServiceShape service) {
    ElixirCodecEmission.writeModule(ctx, clientCodecFileName(ctx, service), buildClientCodecModule(ctx, service));
  }

  static void emitServerCodecModule(ElixirContext ctx, ServiceShape service) {
    ElixirCodecEmission.writeModule(ctx, serverCodecFileName(ctx, service), buildServerCodecModule(ctx, service));
  }

  static List<Function> enumHelperFunctions(
      Model model, ServiceShape service, SymbolProvider sp) {
    List<Function> functions = new ArrayList<>();
    for (EnumShape enumShape : ElixirRestJsonSupport.reachableEnumShapes(model, service)) {
      functions.addAll(ElixirEnumHelperIr.enumDecodeEncode(enumShape, sp));
    }
    for (IntEnumShape intEnumShape : ElixirRestJsonSupport.reachableIntEnumShapes(model, service)) {
      functions.addAll(ElixirEnumHelperIr.intEnumDecodeEncode(intEnumShape, sp));
    }
    return functions;
  }

  static List<Function> structureHelperFunctions(
      Model model, ServiceShape service, SymbolProvider sp) {
    List<Function> functions = new ArrayList<>();
    Set<StructureShape> structures = new LinkedHashSet<>();
    Set<StructureShape> listElementStructures = new LinkedHashSet<>();
    HttpBindingIndex httpIndex = HttpBindingIndex.of(model);
    ElixirRestJsonSupport.collectStructureHelperTargets(
        model, service, httpIndex, structures, listElementStructures);
    for (StructureShape structure : structures) {
      functions.addAll(
          ElixirStructureHelperIr.structureDecodeEncode(model, httpIndex, structure, sp));
      if (listElementStructures.contains(structure)) {
        functions.addAll(ElixirStructureHelperIr.structureListDecodeEncode(structure));
      }
    }
    return functions;
  }

  static List<Function> unionHelperFunctions(
      Model model, ServiceShape service, SymbolProvider sp) {
    List<Function> functions = new ArrayList<>();
    for (UnionShape union : ElixirRestJsonSupport.reachableUnionShapes(model, service)) {
      functions.addAll(ElixirUnionHelperIr.unionDecodeEncode(union, sp));
    }
    return functions;
  }

  static List<Function> mapHelperFunctions(Model model, ServiceShape service, SymbolProvider sp) {
    List<Function> functions = new ArrayList<>();
    HttpBindingIndex httpIndex = HttpBindingIndex.of(model);
    for (MapShape map : ElixirRestJsonSupport.reachableTypedMapShapes(model, service)) {
      functions.addAll(ElixirMapHelperIr.mapDecodeEncode(model, httpIndex, map, sp));
    }
    return functions;
  }

  static List<Function> privateCodecHelpers(Model model, ServiceShape service) {
    List<Function> functions = new ArrayList<>();
    functions.addAll(ElixirCodecHelperIr.toBinary(ElixirCodecHelperIr.ToBinaryVariant.REST_JSON));
    functions.addAll(ElixirCodecHelperIr.encodeQueryValueRestJson());
    functions.addAll(ElixirCodecHelperIr.uriEncode());
    functions.addAll(ElixirCodecHelperIr.uriDecode());
    functions.addAll(ElixirCodecHelperIr.decodeQueryParam());
    functions.addAll(ElixirCodecHelperIr.prefixHeadersToList());
    functions.addAll(ElixirCodecHelperIr.prefixHeadersFromList());
    functions.addAll(ElixirCodecHelperIr.decodeJsonBody());
    functions.addAll(ElixirCodecHelperIr.headerValue());
    functions.addAll(ElixirCodecHelperIr.headerValueRaw());
    functions.addAll(ElixirCodecHelperIr.contentTypeMatches());
    functions.addAll(ElixirCodecHelperIr.ctBase());
    functions.addAll(ElixirCodecHelperIr.decodeSparseList());
    functions.addAll(ElixirCodecHelperIr.decodeList());
    functions.addAll(ElixirCodecHelperIr.decodeSparseMap());
    functions.addAll(ElixirCodecHelperIr.encodeSparseList());
    functions.addAll(ElixirCodecHelperIr.encodeSparseMap());
    functions.addAll(ElixirCodecHelperIr.encodeTimestampEpochSeconds());
    functions.addAll(ElixirCodecHelperIr.encodeTimestampDateTime());
    functions.addAll(ElixirCodecHelperIr.decodeTimestampEpochSeconds());
    functions.addAll(ElixirCodecHelperIr.decodeTimestampDateTime());
    functions.addAll(ElixirCodecHelperIr.generateUuid());

    boolean checksumBindings = ElixirHttpChecksumIr.serviceHasChecksumOperations(model, service);
    boolean compressionBindings =
        ElixirRestJsonSupport.serviceHasCompressionOperations(model, service);
    if (!checksumBindings && compressionBindings) {
      functions.addAll(ElixirCodecHelperIr.headersSet());
    }
    return functions;
  }

  static List<Function> codecHelperFunctions(Model model, ServiceShape service, SymbolProvider sp) {
    List<Function> functions = new ArrayList<>();
    functions.addAll(structureHelperFunctions(model, service, sp));
    functions.addAll(enumHelperFunctions(model, service, sp));
    functions.addAll(unionHelperFunctions(model, service, sp));
    functions.addAll(mapHelperFunctions(model, service, sp));
    functions.addAll(privateCodecHelpers(model, service));
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
      String eventStreamModule,
      boolean encodeWithConfig) {
    List<Function> functions = new ArrayList<>();
    for (OperationShape op : operations) {
      functions.addAll(
          ElixirRestJsonOperationIr.buildEncodeRequest(
              model,
              service,
              op,
              httpIndex,
              sp,
              typesMod,
              runtimeMod,
              encodeWithConfig,
              eventStreamModule));
      functions.addAll(
          ElixirRestJsonOperationIr.buildDecodeRequest(
              model, op, httpIndex, sp, typesMod, runtimeMod, eventStreamModule));
      functions.addAll(
          ElixirRestJsonOperationIr.buildDecodeResponse(
              model, service, op, httpIndex, sp, typesMod, runtimeMod));
    }
    for (OperationShape op : operations) {
      functions.addAll(ElixirRestJsonOperationIr.buildErrorDispatch(model, op, sp, typesMod));
    }
    if (encodeWithConfig) {
      functions.addAll(ElixirHostLabelIr.buildHostFunctions(model, service, sp));
    }
    functions.addAll(codecHelperFunctions(model, service, sp));
    return functions;
  }

  static List<Function> serverCodecFunctions(
      Model model,
      ServiceShape service,
      List<OperationShape> operations,
      HttpBindingIndex httpIndex,
      SymbolProvider sp,
      String typesMod,
      String runtimeMod,
      String eventStreamModule) {
    List<Function> functions = new ArrayList<>();
    Set<ShapeId> emittedErrorEncoders = new LinkedHashSet<>();
    for (OperationShape op : operations) {
      functions.addAll(
          ElixirRestJsonOperationIr.buildDecodeRequest(
              model, op, httpIndex, sp, typesMod, runtimeMod, eventStreamModule));
      functions.addAll(
          ElixirRestJsonOperationIr.buildEncodeResponse(
              model, op, httpIndex, sp, typesMod, runtimeMod));
      for (ShapeId errorId : op.getErrors()) {
        if (emittedErrorEncoders.add(errorId)) {
          functions.addAll(
              ElixirRestJsonOperationIr.buildErrorResponseEncoder(model, errorId, sp, typesMod));
        }
      }
    }
    functions.addAll(codecHelperFunctions(model, service, sp));
    return functions;
  }

  static List<Function> sharedCodecHelpers(Model model, ServiceShape service, SymbolProvider sp) {
    return codecHelperFunctions(model, service, sp);
  }

  private static BeamElixirLayout layout(ElixirContext ctx, ServiceShape service) {
    return new BeamElixirLayout(ctx.settings(), service.getId().getNamespace(), service);
  }
}
