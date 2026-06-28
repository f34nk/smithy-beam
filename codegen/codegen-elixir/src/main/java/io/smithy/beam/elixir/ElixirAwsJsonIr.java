package io.smithy.beam.elixir;

import io.smithy.beam.core.BeamProtocolIds;
import io.smithy.beam.ir.elixir.ExFunction;
import java.util.ArrayList;
import java.util.List;
import software.amazon.smithy.codegen.core.SymbolProvider;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.knowledge.HttpBindingIndex;
import software.amazon.smithy.model.shapes.OperationShape;
import software.amazon.smithy.model.shapes.ServiceShape;
import software.amazon.smithy.model.shapes.ShapeId;

final class ElixirAwsJsonIr {
  private static final java.util.Map<ShapeId, String> CONTENT_TYPES =
      java.util.Map.of(
          BeamProtocolIds.AWS_JSON_1_0, "application/x-amz-json-1.0",
          BeamProtocolIds.AWS_JSON_1_1, "application/x-amz-json-1.1");

  private ElixirAwsJsonIr() {}

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
}
