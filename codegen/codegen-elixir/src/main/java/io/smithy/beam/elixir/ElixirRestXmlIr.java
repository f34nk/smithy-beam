package io.smithy.beam.elixir;

import io.beam.dsl.elixir.Alias;
import io.beam.dsl.elixir.Function;
import io.beam.dsl.elixir.Module;
import io.beam.dsl.elixir.Moduledoc;
import io.smithy.beam.core.BeamElixirLayout;
import io.smithy.beam.core.BeamProtocolIds;
import io.smithy.beam.core.BeamS3CustomizationIndex;
import io.smithy.beam.core.BeamXmlBindingIndex;
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

  static Module buildClientCodecModule(ElixirContext ctx, ServiceShape service) {
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
    List<Function> functions =
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

    List<Alias> aliases = new ArrayList<>();
    aliases.add(Alias.of(runtimeMod, "RuntimeTypes"));
    aliases.add(Alias.of(typesMod, "Types"));
    if (BeamS3CustomizationIndex.isS3Service(service)) {
      aliases.add(Alias.of("S3Endpoint"));
    }

    return Module.of(
        moduleName,
        Moduledoc.of("REST-XML codecs for " + service.getId() + " (generated). Do not edit."),
        List.of(),
        aliases,
        List.of(),
        List.of(),
        List.of(),
        List.of(),
        functions);
  }

  static Module buildServerCodecModule(ElixirContext ctx, ServiceShape service) {
    Model model = ctx.model();
    BeamElixirLayout layout = layout(ctx, service);
    HttpBindingIndex httpIndex = HttpBindingIndex.of(model);
    SymbolProvider sp = ctx.symbolProvider();
    String moduleName =
        ElixirSymbolProvider.toModuleName(layout.serverCodecModuleName(BeamProtocolIds.REST_XML));
    String runtimeMod = ElixirSymbolProvider.toModuleName(layout.runtimeTypesModuleName());
    String typesMod = ElixirSymbolProvider.toModuleName(layout.typesModuleName());
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
            BeamXmlBindingIndex.xmlNamespaceUri(service));

    return Module.of(
        moduleName,
        Moduledoc.of(
            "Server REST-XML codecs for " + service.getId() + " (generated). Do not edit."),
        List.of(),
        List.of(Alias.of(runtimeMod, "RuntimeTypes"), Alias.of(typesMod, "Types")),
        List.of(),
        List.of(),
        List.of(),
        List.of(),
        functions);
  }

  static void emitClientCodecModule(ElixirContext ctx, ServiceShape service) {
    ElixirCodecEmission.writeModule(
        ctx, clientCodecFileName(ctx, service), buildClientCodecModule(ctx, service));
  }

  static void emitServerCodecModule(ElixirContext ctx, ServiceShape service) {
    ElixirCodecEmission.writeModule(
        ctx, serverCodecFileName(ctx, service), buildServerCodecModule(ctx, service));
  }

  static List<Function> enumHelperFunctions(Model model, ServiceShape service, SymbolProvider sp) {
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

  static List<Function> clientCodecFunctions(
      Model model,
      ServiceShape service,
      List<OperationShape> operations,
      HttpBindingIndex httpIndex,
      SymbolProvider sp,
      String typesMod,
      String runtimeMod,
      Optional<String> serviceNamespace,
      boolean encodeWithConfig) {
    List<Function> functions = new ArrayList<>();
    for (OperationShape op : operations) {
      functions.addAll(
          ElixirRestXmlOperationIr.buildEncodeRequest(
              model, service, op, httpIndex, sp, typesMod, runtimeMod, encodeWithConfig));
      functions.addAll(
          ElixirRestXmlOperationIr.buildDecodeResponse(model, op, httpIndex, sp, typesMod));
      functions.addAll(ElixirRestXmlOperationIr.buildErrorDispatch(model, op, sp, typesMod));
    }
    functions.addAll(enumHelperFunctions(model, service, sp));
    functions.addAll(xmlHelperFunctions(serviceNamespace));
    functions.addAll(sharedClientCodecHelpers());
    if (encodeWithConfig) {
      functions.addAll(ElixirHostLabelIr.buildHostFunctions(model, service, sp));
    }
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
      Optional<String> serviceNamespace) {
    List<Function> functions = new ArrayList<>();
    for (OperationShape op : operations) {
      functions.addAll(
          ElixirRestXmlOperationIr.buildDecodeRequest(model, op, httpIndex, sp, typesMod));
      functions.addAll(
          ElixirRestXmlOperationIr.buildEncodeResponse(
              model, op, httpIndex, sp, typesMod, runtimeMod));
    }
    functions.addAll(enumHelperFunctions(model, service, sp));
    functions.addAll(xmlHelperFunctions(serviceNamespace));
    return functions;
  }

  private static BeamElixirLayout layout(ElixirContext ctx, ServiceShape service) {
    return new BeamElixirLayout(ctx.settings(), service.getId().getNamespace(), service);
  }
}
