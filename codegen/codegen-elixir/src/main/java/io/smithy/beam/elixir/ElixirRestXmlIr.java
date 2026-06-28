package io.smithy.beam.elixir;

import io.smithy.beam.core.BeamS3CustomizationIndex;
import io.smithy.beam.core.BeamXmlBindingIndex;
import io.smithy.beam.ir.elixir.ExFunction;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import software.amazon.smithy.codegen.core.SymbolProvider;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.knowledge.HttpBindingIndex;
import software.amazon.smithy.model.shapes.EnumShape;
import software.amazon.smithy.model.shapes.IntEnumShape;
import software.amazon.smithy.model.shapes.OperationShape;
import software.amazon.smithy.model.shapes.ServiceShape;

final class ElixirRestXmlIr {
  private ElixirRestXmlIr() {}

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

  static List<ExFunction> sharedClientCodecHelpers() {
    return List.of(
        ElixirCodecHelperIr.prefixHeadersToList(),
        ElixirCodecHelperIr.prefixHeadersFromList(),
        ElixirCodecHelperIr.generateUuid(),
        ElixirCodecHelperIr.toBinary(ElixirCodecHelperIr.ToBinaryVariant.XML_QUERY),
        ElixirCodecHelperIr.encodeQueryValueXmlQuery());
  }

  static boolean serviceEncodesWithConfig(Model model, ServiceShape service) {
    return ElixirRestJsonSupport.serviceHasHostLabelOperations(model, service)
        || BeamS3CustomizationIndex.of(model).serviceUsesBucketAddressing(service);
  }

  static List<ExFunction> clientCodecFunctions(
      Model model,
      ServiceShape service,
      List<OperationShape> operations,
      HttpBindingIndex httpIndex,
      SymbolProvider sp,
      String typesMod,
      String runtimeMod,
      Optional<String> serviceNamespace,
      boolean encodeWithConfig) {
    List<ExFunction> functions = new ArrayList<>();
    functions.add(ElixirXmlCodecIr.xmlNamespace(serviceNamespace));
    for (OperationShape op : operations) {
      functions.add(
          ElixirRestXmlOperationIr.buildEncodeRequest(
              model, service, op, httpIndex, sp, typesMod, runtimeMod, encodeWithConfig));
      ExFunction decodeResponse =
          ElixirRestXmlOperationIr.buildDecodeResponse(model, op, httpIndex, sp, typesMod);
      functions.add(decodeResponse);
      ExFunction errorDispatch =
          ElixirRestXmlOperationIr.buildErrorDispatch(model, op, sp, typesMod);
      if (errorDispatch != null) {
        functions.add(errorDispatch);
      }
    }
    functions.addAll(enumHelperFunctions(model, service, sp));
    functions.addAll(ElixirXmlCodecIr.restXmlEncodeHelpers());
    functions.addAll(ElixirXmlCodecIr.restXmlDecodeHelpers());
    functions.addAll(sharedClientCodecHelpers());
    if (ElixirHttpChecksumIr.serviceHasChecksumOperations(model, service)) {
      functions.addAll(ElixirHttpChecksumIr.checksumHelperFunctions());
    }
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
      Optional<String> serviceNamespace) {
    List<ExFunction> functions = new ArrayList<>();
    functions.add(ElixirXmlCodecIr.xmlNamespace(serviceNamespace));
    for (OperationShape op : operations) {
      functions.add(
          ElixirRestXmlOperationIr.buildDecodeRequest(model, op, httpIndex, sp, typesMod));
      functions.add(
          ElixirRestXmlOperationIr.buildEncodeResponse(
              model, op, httpIndex, sp, typesMod, runtimeMod));
    }
    functions.addAll(enumHelperFunctions(model, service, sp));
    functions.addAll(ElixirXmlCodecIr.restXmlEncodeHelpers());
    functions.addAll(ElixirXmlCodecIr.restXmlDecodeHelpers());
    if (ElixirHttpChecksumIr.serviceHasChecksumOperations(model, service)) {
      functions.addAll(ElixirHttpChecksumIr.checksumHelperFunctions());
    }
    return functions;
  }
}
