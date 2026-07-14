package io.smithy.beam.elixir;

import io.beam.ir.elixir.Function;
import io.smithy.beam.core.BeamElixirLayout;
import io.smithy.beam.ir.elixir.ExAliasAttr;
import io.smithy.beam.ir.elixir.ExFunction;
import io.smithy.beam.ir.elixir.ExModule;
import io.smithy.beam.ir.elixir.ExModuledoc;
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

  static ExModule buildClientCodecModule(ElixirContext ctx, ServiceShape service) {
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
    List<ExFunction> functions =
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

    return ExModule.module(
        moduleName,
        List.of(
            ExModuledoc.moduledoc(
                "REST JSON 1 codecs for " + service.getId() + " (generated). Do not edit.")),
        List.of(
            ExAliasAttr.alias(runtimeMod, "RuntimeTypes"), ExAliasAttr.alias(typesMod, "Types")),
        functions);
  }

  static ExModule buildServerCodecModule(ElixirContext ctx, ServiceShape service) {
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
    List<ExFunction> functions =
        serverCodecFunctions(
            model, service, operations, httpIndex, sp, typesMod, runtimeMod, eventStreamModule);

    return ExModule.module(
        moduleName,
        List.of(
            ExModuledoc.moduledoc(
                "Server REST JSON 1 codecs for " + service.getId() + " (generated). Do not edit.")),
        List.of(
            ExAliasAttr.alias(runtimeMod, "RuntimeTypes"), ExAliasAttr.alias(typesMod, "Types")),
        functions);
  }

  static void emitClientCodecModule(ElixirContext ctx, ServiceShape service) {
    ExModule module = buildClientCodecModule(ctx, service);
    ElixirCodecEmission.writeModule(ctx, clientCodecFileName(ctx, service), module);
  }

  static void emitServerCodecModule(ElixirContext ctx, ServiceShape service) {
    ExModule module = buildServerCodecModule(ctx, service);
    ElixirCodecEmission.writeModule(ctx, serverCodecFileName(ctx, service), module);
  }

  static List<ExFunction> enumHelperFunctions(
      Model model, ServiceShape service, SymbolProvider sp) {
    List<ExFunction> functions = new ArrayList<>();
    @SuppressWarnings("unused")
    List<Function> enumHelpers = new ArrayList<>();
    for (EnumShape enumShape : ElixirRestJsonSupport.reachableEnumShapes(model, service)) {
      enumHelpers.addAll(ElixirEnumHelperIr.enumDecodeEncode(enumShape, sp));
    }
    for (IntEnumShape intEnumShape : ElixirRestJsonSupport.reachableIntEnumShapes(model, service)) {
      enumHelpers.addAll(ElixirEnumHelperIr.intEnumDecodeEncode(intEnumShape, sp));
    }
    return functions;
  }

  static List<ExFunction> structureHelperFunctions(
      Model model, ServiceShape service, SymbolProvider sp) {
    List<ExFunction> functions = new ArrayList<>();
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

  static List<ExFunction> unionHelperFunctions(
      Model model, ServiceShape service, SymbolProvider sp) {
    List<ExFunction> functions = new ArrayList<>();
    @SuppressWarnings("unused")
    List<Function> unionHelpers = new ArrayList<>();
    for (UnionShape union : ElixirRestJsonSupport.reachableUnionShapes(model, service)) {
      unionHelpers.addAll(ElixirUnionHelperIr.unionDecodeEncode(union, sp));
    }
    return functions;
  }

  static List<ExFunction> mapHelperFunctions(Model model, ServiceShape service, SymbolProvider sp) {
    List<ExFunction> functions = new ArrayList<>();
    HttpBindingIndex httpIndex = HttpBindingIndex.of(model);
    @SuppressWarnings("unused")
    List<Function> mapHelpers = new ArrayList<>();
    for (MapShape map : ElixirRestJsonSupport.reachableTypedMapShapes(model, service)) {
      mapHelpers.addAll(ElixirMapHelperIr.mapDecodeEncode(model, httpIndex, map, sp));
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

  static List<ExFunction> clientCodecFunctions(
      Model model,
      ServiceShape service,
      List<OperationShape> operations,
      HttpBindingIndex httpIndex,
      SymbolProvider sp,
      String typesMod,
      String runtimeMod,
      String eventStreamModule,
      boolean encodeWithConfig) {
    List<ExFunction> functions = new ArrayList<>();
    for (OperationShape op : operations) {
      functions.add(
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
      functions.add(
          ElixirRestJsonOperationIr.buildDecodeRequest(
              model, op, httpIndex, sp, typesMod, runtimeMod, eventStreamModule));
      functions.add(
          ElixirRestJsonOperationIr.buildDecodeResponse(
              model, service, op, httpIndex, sp, typesMod, runtimeMod));
    }
    for (OperationShape op : operations) {
      functions.add(ElixirRestJsonOperationIr.buildErrorDispatch(model, op, sp, typesMod));
    }
    functions.addAll(structureHelperFunctions(model, service, sp));
    functions.addAll(enumHelperFunctions(model, service, sp));
    functions.addAll(unionHelperFunctions(model, service, sp));
    functions.addAll(mapHelperFunctions(model, service, sp));
    @SuppressWarnings("unused")
    List<Function> privateHelpers = privateCodecHelpers(model, service);
    if (encodeWithConfig) {
      functions.addAll(ElixirHostLabelIr.buildHostFunctions(model, service, sp));
    }
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
      String eventStreamModule) {
    List<ExFunction> functions = new ArrayList<>();
    Set<ShapeId> emittedErrorEncoders = new LinkedHashSet<>();
    for (OperationShape op : operations) {
      functions.add(
          ElixirRestJsonOperationIr.buildDecodeRequest(
              model, op, httpIndex, sp, typesMod, runtimeMod, eventStreamModule));
      functions.add(
          ElixirRestJsonOperationIr.buildEncodeResponse(
              model, op, httpIndex, sp, typesMod, runtimeMod));
      for (ShapeId errorId : op.getErrors()) {
        if (emittedErrorEncoders.add(errorId)) {
          functions.add(
              ElixirRestJsonOperationIr.buildErrorResponseEncoder(model, errorId, sp, typesMod));
        }
      }
    }
    functions.addAll(structureHelperFunctions(model, service, sp));
    functions.addAll(enumHelperFunctions(model, service, sp));
    functions.addAll(unionHelperFunctions(model, service, sp));
    functions.addAll(mapHelperFunctions(model, service, sp));
    @SuppressWarnings("unused")
    List<Function> privateHelpers = privateCodecHelpers(model, service);
    return functions;
  }

  static List<ExFunction> sharedCodecHelpers(Model model, ServiceShape service, SymbolProvider sp) {
    List<ExFunction> functions = new ArrayList<>();
    functions.addAll(structureHelperFunctions(model, service, sp));
    functions.addAll(enumHelperFunctions(model, service, sp));
    functions.addAll(unionHelperFunctions(model, service, sp));
    functions.addAll(mapHelperFunctions(model, service, sp));
    @SuppressWarnings("unused")
    List<Function> privateHelpers = privateCodecHelpers(model, service);
    return functions;
  }

  private static BeamElixirLayout layout(ElixirContext ctx, ServiceShape service) {
    return new BeamElixirLayout(ctx.settings(), service.getId().getNamespace(), service);
  }
}
