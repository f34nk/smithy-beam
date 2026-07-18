package io.smithy.beam.elixir;

import io.beam.dsl.elixir.Alias;
import io.beam.dsl.elixir.Function;
import io.beam.dsl.elixir.Module;
import io.beam.dsl.elixir.Moduledoc;
import io.smithy.beam.core.BeamAwsServiceMetadata;
import io.smithy.beam.core.BeamElixirLayout;
import io.smithy.beam.core.BeamProtocolIds;
import io.smithy.beam.core.BeamXmlBindingIndex;
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

final class ElixirAwsQueryDsl {
  private ElixirAwsQueryDsl() {}

  static String clientCodecFileName(
      ElixirContext ctx, ServiceShape service, ShapeId protocolTraitId) {
    return layout(ctx, service).codecModuleName(protocolTraitId) + ".ex";
  }

  static String serverCodecFileName(
      ElixirContext ctx, ServiceShape service, ShapeId protocolTraitId) {
    return layout(ctx, service).codecModuleName(protocolTraitId) + ".ex";
  }

  static Module buildClientCodecModule(
      ElixirContext ctx, ServiceShape service, ShapeId protocolTraitId) {
    boolean ec2Query = BeamProtocolIds.EC2_QUERY.equals(protocolTraitId);
    BeamAwsServiceMetadata.from(service).orElseThrow();
    Model model = ctx.model();
    BeamElixirLayout layout = layout(ctx, service);
    HttpBindingIndex httpIndex = HttpBindingIndex.of(model);
    SymbolProvider sp = ctx.symbolProvider();
    String moduleName =
        ElixirSymbolProvider.toModuleName(layout.codecModuleName(protocolTraitId));
    String runtimeMod = ElixirSymbolProvider.toModuleName(layout.runtimeTypesModuleName());
    String typesMod = ElixirSymbolProvider.toModuleName(layout.typesModuleName());

    List<OperationShape> operations = ElixirTopDown.containedOperationsSorted(model, service);
    List<Function> functions =
        clientCodecFunctions(
            model, service, operations, httpIndex, sp, typesMod, runtimeMod, ec2Query);

    return Module.of(
        moduleName,
        Moduledoc.of("AWS Query codecs for " + service.getId() + " (generated). Do not edit."),
        List.of(),
        List.of(Alias.of(runtimeMod, "RuntimeTypes"), Alias.of(typesMod, "Types")),
        List.of(),
        List.of(),
        List.of(),
        List.of(),
        functions);
  }

  static Module buildServerCodecModule(
      ElixirContext ctx, ServiceShape service, ShapeId protocolTraitId) {
    boolean ec2Query = BeamProtocolIds.EC2_QUERY.equals(protocolTraitId);
    Model model = ctx.model();
    BeamElixirLayout layout = layout(ctx, service);
    SymbolProvider sp = ctx.symbolProvider();
    String moduleName =
        ElixirSymbolProvider.toModuleName(layout.codecModuleName(protocolTraitId));
    String runtimeMod = ElixirSymbolProvider.toModuleName(layout.runtimeTypesModuleName());
    String typesMod = ElixirSymbolProvider.toModuleName(layout.typesModuleName());
    Optional<String> serviceNamespace = BeamXmlBindingIndex.xmlNamespaceUri(service);

    List<OperationShape> operations = ElixirTopDown.containedOperationsSorted(model, service);
    List<Function> functions =
        serverCodecFunctions(
            model, service, operations, sp, typesMod, runtimeMod, serviceNamespace, ec2Query);

    return Module.of(
        moduleName,
        Moduledoc.of(
            "Server AWS Query codecs for " + service.getId() + " (generated). Do not edit."),
        List.of(),
        List.of(Alias.of(runtimeMod, "RuntimeTypes"), Alias.of(typesMod, "Types")),
        List.of(),
        List.of(),
        List.of(),
        List.of(),
        functions);
  }

  static void emitClientCodecModule(
      ElixirContext ctx, ServiceShape service, ShapeId protocolTraitId) {
    ElixirCodecEmission.writeModule(
        ctx,
        clientCodecFileName(ctx, service, protocolTraitId),
        buildClientCodecModule(ctx, service, protocolTraitId));
  }

  static void emitServerCodecModule(
      ElixirContext ctx, ServiceShape service, ShapeId protocolTraitId) {
    ElixirCodecEmission.writeModule(
        ctx,
        serverCodecFileName(ctx, service, protocolTraitId),
        buildServerCodecModule(ctx, service, protocolTraitId));
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

  static List<Function> clientCodecFunctions(
      Model model,
      ServiceShape service,
      List<OperationShape> operations,
      HttpBindingIndex httpIndex,
      SymbolProvider sp,
      String typesMod,
      String runtimeMod,
      boolean ec2Query) {
    List<Function> functions = new ArrayList<>();
    for (OperationShape op : operations) {
      functions.addAll(
          ElixirAwsQueryOperationDsl.buildEncodeRequest(
              model, service, op, httpIndex, sp, typesMod, runtimeMod));
      functions.addAll(
          ElixirAwsQueryOperationDsl.buildDecodeResponse(
              model, service, op, sp, typesMod, runtimeMod, ec2Query));
    }
    functions.addAll(
        ElixirAwsQueryOperationDsl.buildFlattenQueryInput(
            model, httpIndex, sp, inputShapes(model, service), ec2Query));
    functions.addAll(
        ElixirAwsQueryOperationDsl.buildFlattenStructure(
            sp,
            ElixirAwsQueryOperationDsl.nestedQueryStructures(model, inputShapes(model, service)),
            ec2Query));
    functions.addAll(ElixirAwsQueryHelperDsl.queryHelperFunctions(ec2Query));
    functions.addAll(ElixirAwsQueryHelperDsl.xmlHelperFunctions(ec2Query));
    return functions;
  }

  static List<Function> serverCodecFunctions(
      Model model,
      ServiceShape service,
      List<OperationShape> operations,
      SymbolProvider sp,
      String typesMod,
      String runtimeMod,
      Optional<String> serviceNamespace,
      boolean ec2Query) {
    List<Function> functions = new ArrayList<>();
    for (OperationShape op : operations) {
      functions.addAll(
          ElixirAwsQueryOperationDsl.buildServerDecodeRequest(model, op, sp, typesMod, runtimeMod));
      functions.addAll(
          ElixirAwsQueryOperationDsl.buildServerEncodeResponse(
              model, service, op, sp, typesMod, runtimeMod, ec2Query));
    }
    for (StructureShape input : inputShapes(model, service)) {
      functions.addAll(
          ElixirAwsQueryOperationDsl.buildParseInputFromForm(model, sp, typesMod, input, ec2Query));
    }
    for (StructureShape output : outputShapes(model, service)) {
      functions.addAll(ElixirAwsQueryOperationDsl.buildOutputToResultMap(model, sp, output));
    }
    functions.addAll(ElixirAwsQueryHelperDsl.serverDecodeHelpers(ec2Query));
    functions.addAll(ElixirAwsQueryHelperDsl.serverXmlEncodeHelpers(serviceNamespace, ec2Query));
    return functions;
  }

  private static BeamElixirLayout layout(ElixirContext ctx, ServiceShape service) {
    return new BeamElixirLayout(ctx.settings(), service.getId().getNamespace(), service);
  }
}
