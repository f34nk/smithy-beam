package io.smithy.beam.elixir;

import io.beam.ir.elixir.Function;
import io.smithy.beam.core.BeamElixirLayout;
import io.smithy.beam.core.BeamProtocolIds;
import io.smithy.beam.core.BeamS3CustomizationIndex;
import io.smithy.beam.core.BeamXmlBindingIndex;
import io.smithy.beam.ir.elixir.ExAliasAttr;
import io.smithy.beam.ir.elixir.ExFunction;
import io.smithy.beam.ir.elixir.ExModule;
import io.smithy.beam.ir.elixir.ExModuleAttribute;
import io.smithy.beam.ir.elixir.ExModuleEntry;
import io.smithy.beam.ir.elixir.ExModuledoc;
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

  static String clientCodecFileName(ElixirContext ctx, ServiceShape service) {
    return layout(ctx, service).clientCodecModuleName(BeamProtocolIds.REST_XML) + ".ex";
  }

  static String serverCodecFileName(ElixirContext ctx, ServiceShape service) {
    return layout(ctx, service).serverCodecModuleName(BeamProtocolIds.REST_XML) + ".ex";
  }

  static ExModule buildClientCodecModule(ElixirContext ctx, ServiceShape service) {
    Model model = ctx.model();
    BeamElixirLayout layout = layout(ctx, service);
    HttpBindingIndex httpIndex = HttpBindingIndex.of(model);
    SymbolProvider sp = ctx.symbolProvider();
    String moduleName =
        ElixirSymbolProvider.toModuleName(layout.clientCodecModuleName(BeamProtocolIds.REST_XML));
    String runtimeMod = ElixirSymbolProvider.toModuleName(layout.runtimeTypesModuleName());
    String typesMod = ElixirSymbolProvider.toModuleName(layout.typesModuleName());
    List<OperationShape> operations = ElixirTopDown.containedOperationsSorted(model, service);
    boolean encodeWithConfig = ElixirRestXmlSupport.serviceEncodesWithConfig(model, service);
    List<ExModuleEntry> functions =
        clientCodecFunctions(
            model,
            service,
            operations,
            httpIndex,
            sp,
            typesMod,
            runtimeMod,
            BeamXmlBindingIndex.xmlNamespaceUri(service),
            encodeWithConfig);

    List<ExModuleAttribute> aliases = new ArrayList<>();
    aliases.add(ExAliasAttr.alias(runtimeMod, "RuntimeTypes"));
    aliases.add(ExAliasAttr.alias(typesMod, "Types"));
    if (BeamS3CustomizationIndex.isS3Service(service)) {
      aliases.add(ExAliasAttr.alias("S3Endpoint"));
    }

    return ExModule.module(
        moduleName,
        List.of(
            ExModuledoc.moduledoc(
                "REST-XML codecs for " + service.getId() + " (generated). Do not edit.")),
        aliases,
        functions);
  }

  static ExModule buildServerCodecModule(ElixirContext ctx, ServiceShape service) {
    Model model = ctx.model();
    BeamElixirLayout layout = layout(ctx, service);
    HttpBindingIndex httpIndex = HttpBindingIndex.of(model);
    SymbolProvider sp = ctx.symbolProvider();
    String moduleName =
        ElixirSymbolProvider.toModuleName(layout.serverCodecModuleName(BeamProtocolIds.REST_XML));
    String runtimeMod = ElixirSymbolProvider.toModuleName(layout.runtimeTypesModuleName());
    String typesMod = ElixirSymbolProvider.toModuleName(layout.typesModuleName());
    List<OperationShape> operations = ElixirTopDown.containedOperationsSorted(model, service);
    List<ExModuleEntry> functions =
        serverCodecFunctions(
            model,
            service,
            operations,
            httpIndex,
            sp,
            typesMod,
            runtimeMod,
            BeamXmlBindingIndex.xmlNamespaceUri(service));

    return ExModule.module(
        moduleName,
        List.of(
            ExModuledoc.moduledoc(
                "Server REST-XML codecs for " + service.getId() + " (generated). Do not edit.")),
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

  static List<Function> sharedClientCodecHelpers() {
    List<Function> functions = new ArrayList<>();
    functions.addAll(ElixirCodecHelperIr.prefixHeadersToList());
    functions.addAll(ElixirCodecHelperIr.prefixHeadersFromList());
    functions.addAll(ElixirCodecHelperIr.headerValue());
    functions.addAll(ElixirCodecHelperIr.headerValueRaw());
    functions.addAll(ElixirCodecHelperIr.generateUuid());
    functions.addAll(ElixirCodecHelperIr.toBinary(ElixirCodecHelperIr.ToBinaryVariant.XML_QUERY));
    functions.addAll(ElixirCodecHelperIr.encodeQueryValueXmlQuery());
    return functions;
  }

  static List<Function> xmlHelperFunctions(Optional<String> serviceNamespace) {
    List<Function> functions = new ArrayList<>();
    functions.addAll(ElixirXmlCodecIr.xmlNamespace(serviceNamespace));
    functions.addAll(ElixirXmlCodecIr.restXmlEncodeHelpers());
    functions.addAll(ElixirXmlCodecIr.restXmlDecodeHelpers());
    return functions;
  }

  static boolean serviceEncodesWithConfig(Model model, ServiceShape service) {
    return ElixirRestXmlSupport.serviceEncodesWithConfig(model, service);
  }

  static List<ExModuleEntry> clientCodecFunctions(
      Model model,
      ServiceShape service,
      List<OperationShape> operations,
      HttpBindingIndex httpIndex,
      SymbolProvider sp,
      String typesMod,
      String runtimeMod,
      Optional<String> serviceNamespace,
      boolean encodeWithConfig) {
    List<ExFunction> operationFunctions = new ArrayList<>();
    for (OperationShape op : operations) {
      operationFunctions.add(
          ElixirRestXmlOperationIr.buildEncodeRequest(
              model, service, op, httpIndex, sp, typesMod, runtimeMod, encodeWithConfig));
      ExFunction decodeResponse =
          ElixirRestXmlOperationIr.buildDecodeResponse(model, op, httpIndex, sp, typesMod);
      operationFunctions.add(decodeResponse);
      ExFunction errorDispatch =
          ElixirRestXmlOperationIr.buildErrorDispatch(model, op, sp, typesMod);
      if (errorDispatch != null) {
        operationFunctions.add(errorDispatch);
      }
    }
    List<Function> helperFunctions = new ArrayList<>();
    helperFunctions.addAll(enumHelperFunctions(model, service, sp));
    helperFunctions.addAll(xmlHelperFunctions(serviceNamespace));
    helperFunctions.addAll(sharedClientCodecHelpers());
    if (encodeWithConfig) {
      helperFunctions.addAll(ElixirHostLabelIr.buildHostFunctions(model, service, sp));
    }
    return ElixirBeamIrBridge.moduleEntries(operationFunctions, helperFunctions);
  }

  static List<ExModuleEntry> serverCodecFunctions(
      Model model,
      ServiceShape service,
      List<OperationShape> operations,
      HttpBindingIndex httpIndex,
      SymbolProvider sp,
      String typesMod,
      String runtimeMod,
      Optional<String> serviceNamespace) {
    List<ExFunction> operationFunctions = new ArrayList<>();
    for (OperationShape op : operations) {
      operationFunctions.add(
          ElixirRestXmlOperationIr.buildDecodeRequest(model, op, httpIndex, sp, typesMod));
      operationFunctions.add(
          ElixirRestXmlOperationIr.buildEncodeResponse(
              model, op, httpIndex, sp, typesMod, runtimeMod));
    }
    List<Function> helperFunctions = new ArrayList<>();
    helperFunctions.addAll(enumHelperFunctions(model, service, sp));
    helperFunctions.addAll(xmlHelperFunctions(serviceNamespace));
    return ElixirBeamIrBridge.moduleEntries(operationFunctions, helperFunctions);
  }

  private static List<ExFunction> concat(List<ExFunction> left, List<ExFunction> right) {
    List<ExFunction> combined = new ArrayList<>(left.size() + right.size());
    combined.addAll(left);
    combined.addAll(right);
    return combined;
  }

  private static BeamElixirLayout layout(ElixirContext ctx, ServiceShape service) {
    return new BeamElixirLayout(ctx.settings(), service.getId().getNamespace(), service);
  }
}
