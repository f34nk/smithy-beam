package io.smithy.beam.elixir;

import io.smithy.beam.core.BeamXmlBindingIndex;
import io.smithy.beam.ir.elixir.ExFunction;
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
import software.amazon.smithy.model.shapes.StructureShape;

final class ElixirAwsQueryIr {
  private ElixirAwsQueryIr() {}

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
    functions.add(ElixirXmlCodecIr.xmlNamespace(serviceNamespace));
    for (OperationShape op : operations) {
      functions.add(
          ElixirAwsQueryOperationIr.buildServerDecodeRequest(
              model, op, sp, typesMod, runtimeMod));
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
}
