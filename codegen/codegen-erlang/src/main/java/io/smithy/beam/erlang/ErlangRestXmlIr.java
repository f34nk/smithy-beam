package io.smithy.beam.erlang;

import io.smithy.beam.ir.erlang.ErlFunction;
import software.amazon.smithy.codegen.core.SymbolProvider;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.knowledge.HttpBindingIndex;
import software.amazon.smithy.model.shapes.EnumShape;
import software.amazon.smithy.model.shapes.IntEnumShape;
import software.amazon.smithy.model.shapes.OperationShape;
import software.amazon.smithy.model.shapes.ServiceShape;
import software.amazon.smithy.model.shapes.ShapeId;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

final class ErlangRestXmlIr {
    private ErlangRestXmlIr() {}

    static ErlFunction encodeRequest(
            Model model,
            ServiceShape service,
            OperationShape op,
            HttpBindingIndex httpIndex,
            SymbolProvider sp,
            boolean encodeWithConfig) {
        return ErlangRestXmlOperationIr.buildEncodeRequest(
                model, service, op, httpIndex, sp, encodeWithConfig);
    }

    static ErlFunction decodeRequest(
            Model model,
            OperationShape op,
            HttpBindingIndex httpIndex,
            SymbolProvider sp) {
        return ErlangRestXmlOperationIr.buildDecodeRequest(model, op, httpIndex, sp);
    }

    static List<ErlFunction> decodeResponse(
            Model model,
            OperationShape op,
            HttpBindingIndex httpIndex,
            SymbolProvider sp) {
        return ErlangRestXmlOperationIr.buildDecodeResponse(model, op, httpIndex, sp);
    }

    static ErlFunction encodeResponse(
            Model model,
            OperationShape op,
            HttpBindingIndex httpIndex,
            SymbolProvider sp) {
        return ErlangRestXmlOperationIr.buildEncodeResponse(model, op, httpIndex, sp);
    }

    static List<ErlFunction> enumHelperFunctions(Model model, ServiceShape service, SymbolProvider sp) {
        List<ErlFunction> functions = new ArrayList<>();
        for (EnumShape enumShape : ErlangRestXmlEmitter.reachableEnumShapes(model, service)) {
            functions.addAll(ErlangEnumHelperIr.enumDecodeEncode(enumShape, sp));
        }
        for (IntEnumShape intEnumShape : ErlangRestXmlEmitter.reachableIntEnumShapes(model, service)) {
            functions.addAll(ErlangEnumHelperIr.intEnumDecodeEncode(intEnumShape, sp));
        }
        return functions;
    }

    static List<ErlFunction> xmlDecodeHelpers() {
        return ErlangXmlCodecIr.restXmlDecodeHelpers();
    }

    static List<ErlFunction> xmlEncodeHelpers() {
        return ErlangXmlCodecIr.restXmlEncodeHelpers();
    }

    static List<ErlFunction> clientCodecFunctions(
            Model model,
            ServiceShape service,
            List<OperationShape> operations,
            HttpBindingIndex httpIndex,
            SymbolProvider sp,
            Optional<String> serviceNamespace,
            boolean encodeWithConfig) {
        List<ErlFunction> functions = new ArrayList<>();
        functions.add(ErlangXmlCodecIr.xmlNamespace(serviceNamespace));
        for (OperationShape op : operations) {
            functions.add(encodeRequest(model, service, op, httpIndex, sp, encodeWithConfig));
            functions.add(decodeRequest(model, op, httpIndex, sp));
            functions.addAll(decodeResponse(model, op, httpIndex, sp));
        }
        functions.addAll(enumHelperFunctions(model, service, sp));
        functions.addAll(xmlEncodeHelpers());
        functions.addAll(xmlDecodeHelpers());
        functions.add(ErlangCodecHelperIr.prefixHeadersToList());
        functions.add(ErlangCodecHelperIr.prefixHeadersFromList());
        functions.add(ErlangCodecHelperIr.generateUuid());
        if (ErlangHttpChecksumEmitter.serviceHasChecksumOperations(model, service)) {
            functions.addAll(ErlangHttpChecksumIr.checksumHelperFunctions());
        }
        if (encodeWithConfig) {
            functions.addAll(ErlangHostLabelIr.buildHostFunctions(model, service, sp));
        }
        return functions;
    }

    static List<ErlFunction> serverCodecFunctions(
            Model model,
            ServiceShape service,
            List<OperationShape> operations,
            HttpBindingIndex httpIndex,
            SymbolProvider sp,
            Optional<String> serviceNamespace) {
        List<ErlFunction> functions = new ArrayList<>();
        functions.add(ErlangXmlCodecIr.xmlNamespace(serviceNamespace));
        Set<ShapeId> emittedErrorEncoders = new LinkedHashSet<>();
        for (OperationShape op : operations) {
            functions.add(decodeRequest(model, op, httpIndex, sp));
            functions.add(encodeResponse(model, op, httpIndex, sp));
            for (ShapeId errorId : op.getErrors()) {
                if (emittedErrorEncoders.add(errorId)) {
                    functions.add(ErlangRestXmlOperationIr.buildErrorResponseEncoder(model, errorId, sp));
                }
            }
        }
        functions.addAll(enumHelperFunctions(model, service, sp));
        functions.addAll(xmlEncodeHelpers());
        functions.addAll(xmlDecodeHelpers());
        return functions;
    }
}
