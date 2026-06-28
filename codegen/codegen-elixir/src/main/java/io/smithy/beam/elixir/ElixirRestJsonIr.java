package io.smithy.beam.elixir;

import io.smithy.beam.core.BeamElixirLayout;
import io.smithy.beam.ir.elixir.ExAliasAttr;
import io.smithy.beam.ir.elixir.ExFunction;
import io.smithy.beam.ir.elixir.ExModuledoc;
import io.smithy.beam.ir.elixir.ExModule;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import software.amazon.smithy.codegen.core.SymbolProvider;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.knowledge.HttpBindingIndex;
import software.amazon.smithy.model.shapes.EnumShape;
import software.amazon.smithy.model.shapes.IntEnumShape;
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
                "Server REST JSON 1 codecs for "
                    + service.getId()
                    + " (generated). Do not edit.")),
        List.of(
            ExAliasAttr.alias(runtimeMod, "RuntimeTypes"), ExAliasAttr.alias(typesMod, "Types")),
        functions);
  }

  static void emitClientCodecModule(ElixirContext ctx, ServiceShape service) {
    ExModule module = buildClientCodecModule(ctx, service);
    ElixirCodecEmission.writeModule(ctx, clientCodecFileName(ctx, service), module);
  }

  static void emitServerCodecModule(ElixirContext ctx, ServiceShape service) {
    ElixirCodecEmission.emitRuntimeHelpersIfNeeded(ctx, service, true);
    ExModule module = buildServerCodecModule(ctx, service);
    ElixirCodecEmission.writeModule(ctx, serverCodecFileName(ctx, service), module);
  }

  static List<ExFunction> enumHelperFunctions(
      Model model, ServiceShape service, SymbolProvider sp) {
    List<ExFunction> functions = new ArrayList<>();
    for (EnumShape enumShape : ElixirRestJsonSupport.reachableEnumShapes(model, service)) {
      functions.addAll(ElixirEnumHelperIr.enumDecodeEncode(enumShape, sp));
    }
    for (IntEnumShape intEnumShape : ElixirRestJsonSupport.reachableIntEnumShapes(model, service)) {
      functions.addAll(ElixirEnumHelperIr.intEnumDecodeEncode(intEnumShape, sp));
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
    for (UnionShape union : ElixirRestJsonSupport.reachableUnionShapes(model, service)) {
      functions.addAll(ElixirUnionHelperIr.unionDecodeEncode(union, sp));
    }
    return functions;
  }

  static List<ExFunction> privateCodecHelpers(Model model, ServiceShape service) {
    List<ExFunction> functions = new ArrayList<>();
    functions.add(ElixirCodecHelperIr.toBinary(ElixirCodecHelperIr.ToBinaryVariant.REST_JSON));
    functions.add(ElixirCodecHelperIr.encodeQueryValueRestJson());
    functions.add(ElixirCodecHelperIr.uriEncode());
    functions.add(ElixirCodecHelperIr.uriDecode());
    functions.add(ElixirCodecHelperIr.decodeQueryParam());
    functions.add(ElixirCodecHelperIr.prefixHeadersToList());
    functions.add(ElixirCodecHelperIr.prefixHeadersFromList());
    functions.add(ElixirCodecHelperIr.decodeJsonBody());
    functions.add(ElixirCodecHelperIr.contentTypeMatches());
    functions.add(ElixirCodecHelperIr.ctBase());
    functions.add(ElixirCodecHelperIr.decodeSparseList());
    functions.add(ElixirCodecHelperIr.decodeList());
    functions.add(ElixirCodecHelperIr.decodeSparseMap());
    functions.add(ElixirCodecHelperIr.encodeSparseList());
    functions.add(ElixirCodecHelperIr.encodeSparseMap());
    functions.add(ElixirCodecHelperIr.encodeTimestampEpochSeconds());
    functions.add(ElixirCodecHelperIr.encodeTimestampDateTime());
    functions.add(ElixirCodecHelperIr.decodeTimestampEpochSeconds());
    functions.add(ElixirCodecHelperIr.decodeTimestampDateTime());
    functions.add(ElixirCodecHelperIr.generateUuid());

    boolean checksumBindings = ElixirHttpChecksumIr.serviceHasChecksumOperations(model, service);
    boolean compressionBindings =
        ElixirRestJsonSupport.serviceHasCompressionOperations(model, service);
    if (checksumBindings) {
      functions.addAll(ElixirHttpChecksumIr.checksumHelperFunctions());
    } else if (compressionBindings) {
      functions.add(ElixirCodecHelperIr.headersSet());
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
    functions.addAll(privateCodecHelpers(model, service));
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
              ElixirRestJsonOperationIr.buildErrorResponseEncoder(
                  model, errorId, sp, typesMod));
        }
      }
    }
    functions.addAll(structureHelperFunctions(model, service, sp));
    functions.addAll(enumHelperFunctions(model, service, sp));
    functions.addAll(unionHelperFunctions(model, service, sp));
    functions.addAll(privateCodecHelpers(model, service));
    return functions;
  }

  static List<ExFunction> sharedCodecHelpers(
      Model model, ServiceShape service, SymbolProvider sp) {
    List<ExFunction> functions = new ArrayList<>();
    functions.addAll(structureHelperFunctions(model, service, sp));
    functions.addAll(enumHelperFunctions(model, service, sp));
    functions.addAll(unionHelperFunctions(model, service, sp));
    functions.addAll(privateCodecHelpers(model, service));
    return functions;
  }

  private static BeamElixirLayout layout(ElixirContext ctx, ServiceShape service) {
    return new BeamElixirLayout(ctx.settings(), service.getId().getNamespace(), service);
  }
}
