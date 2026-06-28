package io.smithy.beam.elixir;

import io.smithy.beam.ir.elixir.ExFunction;
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
}
