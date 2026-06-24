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
import java.util.function.Consumer;

final class ErlangAwsQueryIr {
    private ErlangAwsQueryIr() {}

    static ErlFunction encodeRequest(
            Model model,
            ServiceShape service,
            OperationShape op,
            HttpBindingIndex httpIndex,
            SymbolProvider sp) {
        return capture(writer -> ErlangAwsQueryEmitter.emitEncoder(
                writer, model, service, op, httpIndex, sp));
    }

    static ErlFunction decodeResponse(
            Model model,
            ServiceShape service,
            OperationShape op,
            SymbolProvider sp,
            boolean ec2Query) {
        return capture(writer -> ErlangAwsQueryEmitter.emitDecoder(
                writer, model, service, op, sp, ec2Query));
    }

    static ErlFunction flattenInputClause(
            Model model,
            HttpBindingIndex httpIndex,
            SymbolProvider sp,
            StructureShape input,
            boolean ec2Query,
            boolean lastClause) {
        return capture(writer -> ErlangAwsQueryEmitter.emitFlattenInputClause(
                writer, model, httpIndex, sp, input, ec2Query, lastClause));
    }

    static ErlFunction queryHelpers(boolean ec2Query) {
        return capture(writer -> ErlangAwsQueryEmitter.emitQueryHelpers(writer, ec2Query));
    }

    static ErlFunction xmlHelpers(boolean ec2Query) {
        return capture(writer -> ErlangAwsQueryEmitter.emitXmlHelpers(writer, ec2Query));
    }

    static ErlFunction serverDecodeRequest(
            Model model,
            OperationShape op,
            SymbolProvider sp,
            boolean ec2Query) {
        return capture(writer -> ErlangAwsQueryEmitter.emitServerRequestDecoder(
                writer, model, op, sp, ec2Query));
    }

    static ErlFunction serverEncodeResponse(
            Model model,
            ServiceShape service,
            OperationShape op,
            SymbolProvider sp,
            boolean ec2Query) {
        return capture(writer -> ErlangAwsQueryEmitter.emitServerResponseEncoder(
                writer, model, service, op, sp, ec2Query));
    }

    static ErlFunction parseInputFromForm(
            Model model,
            SymbolProvider sp,
            StructureShape input,
            boolean ec2Query) {
        return capture(writer -> ErlangAwsQueryEmitter.emitParseInputFromForm(
                writer, model, sp, input, ec2Query));
    }

    static ErlFunction serverQueryDecodeHelpers(boolean ec2Query) {
        return capture(writer -> ErlangAwsQueryEmitter.emitServerQueryDecodeHelpers(writer, ec2Query));
    }

    static ErlFunction serverXmlEncodeHelpers(boolean ec2Query) {
        return capture(writer -> ErlangAwsQueryEmitter.emitServerXmlEncodeHelpers(writer, ec2Query));
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

    private static ErlFunction capture(Consumer<ErlangWriter> action) {
        ErlangWriter writer = new ErlangWriter("capture.erl");
        action.accept(writer);
        return ErlFunction.rendered(writer.toString().strip());
    }
}
