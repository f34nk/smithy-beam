package io.smithy.beam.elixir;

import io.beam.ir.elixir.Function;
import io.smithy.beam.core.BeamAwsServiceMetadata;
import io.smithy.beam.core.BeamElixirLayout;
import io.smithy.beam.core.BeamProtocolIds;
import io.smithy.beam.core.BeamXmlBindingIndex;
import io.smithy.beam.ir.elixir.ExAliasAttr;
import io.smithy.beam.ir.elixir.ExFunction;
import io.smithy.beam.ir.elixir.ExModule;
import io.smithy.beam.ir.elixir.ExModuledoc;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import software.amazon.smithy.codegen.core.SymbolProvider;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.knowledge.HttpBindingIndex;
import software.amazon.smithy.model.shapes.OperationShape;
import software.amazon.smithy.model.shapes.ServiceShape;
import software.amazon.smithy.model.shapes.ShapeId;
import software.amazon.smithy.model.shapes.StructureShape;

final class ElixirAwsQueryIr {
  private ElixirAwsQueryIr() {}

  static String clientCodecFileName(
      ElixirContext ctx, ServiceShape service, ShapeId protocolTraitId) {
    return layout(ctx, service).clientCodecModuleName(protocolTraitId) + ".ex";
  }

  static String serverCodecFileName(
      ElixirContext ctx, ServiceShape service, ShapeId protocolTraitId) {
    return layout(ctx, service).serverCodecModuleName(protocolTraitId) + ".ex";
  }

  static ExModule buildClientCodecModule(
      ElixirContext ctx, ServiceShape service, ShapeId protocolTraitId) {
    boolean ec2Query = BeamProtocolIds.EC2_QUERY.equals(protocolTraitId);
    BeamAwsServiceMetadata.from(service).orElseThrow();
    Model model = ctx.model();
    BeamElixirLayout layout = layout(ctx, service);
    HttpBindingIndex httpIndex = HttpBindingIndex.of(model);
    SymbolProvider sp = ctx.symbolProvider();
    String moduleName =
        ElixirSymbolProvider.toModuleName(layout.clientCodecModuleName(protocolTraitId));
    String runtimeMod = ElixirSymbolProvider.toModuleName(layout.runtimeTypesModuleName());
    String typesMod = ElixirSymbolProvider.toModuleName(layout.typesModuleName());

    List<OperationShape> operations = ElixirTopDown.containedOperationsSorted(model, service);
    List<ExFunction> functions =
        clientCodecFunctions(
            model, service, operations, httpIndex, sp, typesMod, runtimeMod, ec2Query);

    return ExModule.module(
        moduleName,
        List.of(
            ExModuledoc.moduledoc(
                "AWS Query codecs for " + service.getId() + " (generated). Do not edit.")),
        List.of(
            ExAliasAttr.alias(runtimeMod, "RuntimeTypes"), ExAliasAttr.alias(typesMod, "Types")),
        functions);
  }

  static ExModule buildServerCodecModule(
      ElixirContext ctx, ServiceShape service, ShapeId protocolTraitId) {
    boolean ec2Query = BeamProtocolIds.EC2_QUERY.equals(protocolTraitId);
    Model model = ctx.model();
    BeamElixirLayout layout = layout(ctx, service);
    SymbolProvider sp = ctx.symbolProvider();
    String moduleName =
        ElixirSymbolProvider.toModuleName(layout.serverCodecModuleName(protocolTraitId));
    String runtimeMod = ElixirSymbolProvider.toModuleName(layout.runtimeTypesModuleName());
    String typesMod = ElixirSymbolProvider.toModuleName(layout.typesModuleName());

    List<OperationShape> operations = ElixirTopDown.containedOperationsSorted(model, service);
    List<ExFunction> functions =
        serverCodecFunctions(
            model,
            service,
            operations,
            sp,
            typesMod,
            runtimeMod,
            BeamXmlBindingIndex.xmlNamespaceUri(service),
            ec2Query);

    return ExModule.module(
        moduleName,
        List.of(
            ExModuledoc.moduledoc(
                "Server AWS Query codecs for " + service.getId() + " (generated). Do not edit.")),
        List.of(
            ExAliasAttr.alias(runtimeMod, "RuntimeTypes"), ExAliasAttr.alias(typesMod, "Types")),
        functions);
  }

  static void emitClientCodecModule(
      ElixirContext ctx, ServiceShape service, ShapeId protocolTraitId) {
    ExModule module = buildClientCodecModule(ctx, service, protocolTraitId);
    ElixirCodecEmission.writeModule(
        ctx, clientCodecFileName(ctx, service, protocolTraitId), module);
  }

  static void emitServerCodecModule(
      ElixirContext ctx, ServiceShape service, ShapeId protocolTraitId) {
    ExModule module = buildServerCodecModule(ctx, service, protocolTraitId);
    ElixirCodecEmission.writeModule(
        ctx, serverCodecFileName(ctx, service, protocolTraitId), module);
  }

  static List<StructureShape> inputShapes(Model model, ServiceShape service) {
    Set<StructureShape> shapes = new LinkedHashSet<>();
    for (OperationShape op : ElixirTopDown.containedOperationsSorted(model, service)) {
      shapes.add(model.expectShape(op.getInputShape(), StructureShape.class));
    }
    return new ArrayList<>(shapes);
  }

  static List<StructureShape> outputShapes(Model model, ServiceShape service) {
    Set<StructureShape> shapes = new LinkedHashSet<>();
    for (OperationShape op : ElixirTopDown.containedOperationsSorted(model, service)) {
      shapes.add(model.expectShape(op.getOutputShape(), StructureShape.class));
    }
    return new ArrayList<>(shapes);
  }

  static List<ExFunction> clientCodecFunctions(
      Model model,
      ServiceShape service,
      List<OperationShape> operations,
      HttpBindingIndex httpIndex,
      SymbolProvider sp,
      String typesMod,
      String runtimeMod,
      boolean ec2Query) {
    List<ExFunction> functions = new ArrayList<>();
    for (OperationShape op : operations) {
      functions.add(
          ElixirAwsQueryOperationIr.buildEncodeRequest(
              model, service, op, httpIndex, sp, typesMod, runtimeMod));
      functions.add(
          ElixirAwsQueryOperationIr.buildDecodeResponse(
              model, service, op, sp, typesMod, runtimeMod, ec2Query));
    }
    functions.add(
        ElixirAwsQueryOperationIr.buildFlattenQueryInput(
            model, httpIndex, sp, inputShapes(model, service), ec2Query));
    functions.add(
        ElixirAwsQueryOperationIr.buildFlattenStructure(
            sp,
            ElixirAwsQueryOperationIr.nestedQueryStructures(model, inputShapes(model, service)),
            ec2Query));
    functions.addAll(ElixirAwsQueryHelperIr.queryHelperFunctions(ec2Query));
    functions.addAll(ElixirAwsQueryHelperIr.xmlHelperFunctions(ec2Query));
    return functions;
  }

  static List<ExFunction> serverCodecFunctions(
      Model model,
      ServiceShape service,
      List<OperationShape> operations,
      SymbolProvider sp,
      String typesMod,
      String runtimeMod,
      Optional<String> serviceNamespace,
      boolean ec2Query) {
    List<ExFunction> functions = new ArrayList<>();
    @SuppressWarnings("unused")
    List<Function> xmlHelpers = new ArrayList<>();
    xmlHelpers.addAll(ElixirXmlCodecIr.xmlNamespace(serviceNamespace));
    for (OperationShape op : operations) {
      functions.add(
          ElixirAwsQueryOperationIr.buildServerDecodeRequest(model, op, sp, typesMod, runtimeMod));
      functions.add(
          ElixirAwsQueryOperationIr.buildServerEncodeResponse(
              model, service, op, sp, typesMod, runtimeMod, ec2Query));
    }
    for (StructureShape input : inputShapes(model, service)) {
      functions.add(
          ElixirAwsQueryOperationIr.buildParseInputFromForm(model, sp, typesMod, input, ec2Query));
    }
    for (StructureShape output : outputShapes(model, service)) {
      functions.add(ElixirAwsQueryOperationIr.buildOutputToResultMap(model, sp, output));
    }
    functions.addAll(ElixirAwsQueryHelperIr.serverDecodeHelpers(ec2Query));
    functions.addAll(ElixirAwsQueryHelperIr.serverXmlEncodeHelpers(ec2Query));
    return functions;
  }

  private static BeamElixirLayout layout(ElixirContext ctx, ServiceShape service) {
    return new BeamElixirLayout(ctx.settings(), service.getId().getNamespace(), service);
  }
}
