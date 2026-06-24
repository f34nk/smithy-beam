package io.smithy.beam.erlang;

import io.smithy.beam.ir.erlang.ErlFunction;
import software.amazon.smithy.codegen.core.SymbolProvider;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.knowledge.HttpBindingIndex;
import software.amazon.smithy.model.shapes.OperationShape;
import software.amazon.smithy.model.shapes.ServiceShape;
import software.amazon.smithy.model.shapes.StructureShape;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

final class ErlangAwsQueryIr {
    private ErlangAwsQueryIr() {}

    static ErlFunction encodeRequest(
            Model model,
            ServiceShape service,
            OperationShape op,
            HttpBindingIndex httpIndex,
            SymbolProvider sp) {
        return ErlangAwsQueryOperationIr.buildEncodeRequest(model, service, op, httpIndex, sp);
    }

    static ErlFunction decodeResponse(
            Model model,
            ServiceShape service,
            OperationShape op,
            SymbolProvider sp,
            boolean ec2Query) {
        return ErlangAwsQueryOperationIr.buildDecodeResponse(model, service, op, sp, ec2Query);
    }

    static ErlFunction flattenQueryInput(
            Model model,
            HttpBindingIndex httpIndex,
            SymbolProvider sp,
            List<StructureShape> inputs,
            boolean ec2Query) {
        return ErlangAwsQueryOperationIr.buildFlattenQueryInput(model, httpIndex, sp, inputs, ec2Query);
    }

    static List<ErlFunction> queryHelpers(boolean ec2Query) {
        return ErlangAwsQueryHelperIr.queryHelperFunctions(ec2Query);
    }

    static List<ErlFunction> xmlHelpers(boolean ec2Query) {
        return ErlangAwsQueryHelperIr.xmlHelperFunctions(ec2Query);
    }

    static ErlFunction serverDecodeRequest(
            Model model,
            OperationShape op,
            SymbolProvider sp,
            boolean ec2Query) {
        return ErlangAwsQueryOperationIr.buildServerDecodeRequest(model, op, sp, ec2Query);
    }

    static ErlFunction serverEncodeResponse(
            Model model,
            ServiceShape service,
            OperationShape op,
            SymbolProvider sp,
            boolean ec2Query) {
        return ErlangAwsQueryOperationIr.buildServerEncodeResponse(model, service, op, sp, ec2Query);
    }

    static ErlFunction parseInputFromForm(
            Model model,
            SymbolProvider sp,
            StructureShape input,
            boolean ec2Query) {
        return ErlangAwsQueryOperationIr.buildParseInputFromForm(model, sp, input, ec2Query);
    }

    static List<ErlFunction> serverQueryDecodeHelpers(boolean ec2Query) {
        return ErlangAwsQueryHelperIr.serverQueryDecodeHelperFunctions(ec2Query);
    }

    static List<ErlFunction> serverXmlEncodeHelpers(boolean ec2Query) {
        return ErlangAwsQueryHelperIr.serverXmlEncodeHelperFunctions(ec2Query);
    }

    static List<StructureShape> inputShapes(Model model, ServiceShape service) {
        Set<StructureShape> shapes = new LinkedHashSet<>();
        for (OperationShape op : ErlangTopDown.containedOperationsSorted(model, service)) {
            shapes.add(model.expectShape(op.getInputShape(), StructureShape.class));
        }
        return new ArrayList<>(shapes);
    }

    static void writeFunction(ErlangWriter writer, ErlFunction fn) {
        writer.write("$L", fn.asString());
        writer.write("");
    }

    static void writeFunctions(ErlangWriter writer, List<ErlFunction> functions) {
        for (ErlFunction fn : functions) {
            writeFunction(writer, fn);
        }
    }
}
