package io.smithy.beam.erlang;

import io.smithy.beam.core.BeamAwsQueryFormEncoder;
import io.smithy.beam.core.BeamNameUtils;
import io.smithy.beam.core.BeamXmlBindingIndex;
import io.smithy.beam.core.BeamXmlDecoder;
import io.smithy.beam.ir.erlang.ErlCapturedBlock;
import io.smithy.beam.ir.erlang.ErlAtom;
import io.smithy.beam.ir.erlang.ErlAtomPattern;
import io.smithy.beam.ir.erlang.ErlBinary;
import io.smithy.beam.ir.erlang.ErlCall;
import io.smithy.beam.ir.erlang.ErlCallLocal;
import io.smithy.beam.ir.erlang.ErlCase;
import io.smithy.beam.ir.erlang.ErlClause;
import io.smithy.beam.ir.erlang.ErlExpr;
import io.smithy.beam.ir.erlang.ErlExprBlock;
import io.smithy.beam.ir.erlang.ErlFun;
import io.smithy.beam.ir.erlang.ErlFunction;
import io.smithy.beam.ir.erlang.ErlFunctionDoc;
import io.smithy.beam.ir.erlang.ErlFunctionSpec;
import io.smithy.beam.ir.erlang.ErlInteger;
import io.smithy.beam.ir.erlang.ErlIntegerPattern;
import io.smithy.beam.ir.erlang.ErlList;
import io.smithy.beam.ir.erlang.ErlListComprehension;
import io.smithy.beam.ir.erlang.ErlMap;
import io.smithy.beam.ir.erlang.ErlMapEntry;
import io.smithy.beam.ir.erlang.ErlMatch;
import io.smithy.beam.ir.erlang.ErlOp;
import io.smithy.beam.ir.erlang.ErlRecord;
import io.smithy.beam.ir.erlang.ErlRecordField;
import io.smithy.beam.ir.erlang.ErlRecordFieldPattern;
import io.smithy.beam.ir.erlang.ErlRecordPattern;
import io.smithy.beam.ir.erlang.ErlTuple;
import io.smithy.beam.ir.erlang.ErlTuplePattern;
import io.smithy.beam.ir.erlang.ErlVar;
import io.smithy.beam.ir.erlang.ErlVarPattern;
import software.amazon.smithy.codegen.core.Symbol;
import software.amazon.smithy.codegen.core.SymbolProvider;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.knowledge.HttpBindingIndex;
import software.amazon.smithy.model.shapes.ListShape;
import software.amazon.smithy.model.shapes.MemberShape;
import software.amazon.smithy.model.shapes.OperationShape;
import software.amazon.smithy.model.shapes.ServiceShape;
import software.amazon.smithy.model.shapes.Shape;
import software.amazon.smithy.model.shapes.StructureShape;
import software.amazon.smithy.model.traits.XmlNameTrait;

import java.util.ArrayList;
import java.util.List;

final class ErlangAwsQueryOperationIr {
    private static final ErlVarPattern W = ErlVarPattern.varPattern("_");

    private ErlangAwsQueryOperationIr() {}

    static ErlFunction buildEncodeRequest(
            Model model,
            ServiceShape service,
            OperationShape op,
            HttpBindingIndex httpIndex,
            SymbolProvider sp) {
        String opName = sp.toSymbol(op).getName();
        StructureShape input = model.expectShape(op.getInputShape(), StructureShape.class);
        String inputRecord = recordName(sp.toSymbol(input));
        String inputType = sp.toSymbol(input).getName();
        String action = BeamAwsQueryFormEncoder.operationAction(op, service);
        String version = BeamAwsQueryFormEncoder.serviceVersion(service);

        ErlFunctionSpec spec = ErlFunctionSpec.functionSpec(
                "encode_" + opName + "_request", inputType, "#http_request{}");
        ErlRecordPattern inputPattern = inputBindingHead("Input", inputRecord, input, sp);

        List<ErlExpr> body = new ArrayList<>();
        body.add(ErlMatch.match(
                ErlVarPattern.varPattern("Pairs"),
                ErlList.consList(
                        List.of(
                                ErlTuple.tuple(ErlBinary.binary("Action"), ErlBinary.binary(action)),
                                ErlTuple.tuple(ErlBinary.binary("Version"), ErlBinary.binary(version))),
                        ErlCallLocal.callLocal("flatten_query_input", ErlVar.var("Input")))));
        body.add(ErlMatch.match(
                ErlVarPattern.varPattern("Body"),
                ErlCall.call(
                        "uri_string",
                        "compose_query",
                        ErlListComprehension.comprehension(
                                ErlTuple.tuple(ErlVar.var("K"), ErlCallLocal.callLocal("enc", ErlVar.var("V"))),
                                ErlTuplePattern.tuplePattern(
                                        ErlVarPattern.varPattern("K"),
                                        ErlVarPattern.varPattern("V")),
                                ErlVar.var("Pairs"),
                                ErlOp.op("=/=", ErlVar.var("V"), ErlAtom.atom("undefined"))))));
        body.add(ErlRecord.record(
                "http_request",
                ErlRecordField.field("method", ErlBinary.binary("POST")),
                ErlRecordField.field("path", ErlBinary.binary("/")),
                ErlRecordField.field("query", ErlMap.map()),
                ErlRecordField.field(
                        "headers",
                        ErlList.list(ErlTuple.tuple(
                                ErlBinary.binary("Content-Type"),
                                ErlBinary.binary("application/x-www-form-urlencoded")))),
                ErlRecordField.field("body", ErlVar.var("Body"))));

        return ErlFunction.functionWithDocAndSpec(
                "encode_" + opName + "_request",
                1,
                ErlFunctionDoc.functionDoc("Encode AWS Query request for " + op.getId() + "."),
                spec,
                List.of(ErlClause.clause(List.of(inputPattern), ErlExprBlock.block(body.toArray(ErlExpr[]::new)))));
    }

    static ErlFunction buildFlattenQueryInput(
            Model model,
            HttpBindingIndex httpIndex,
            SymbolProvider sp,
            List<StructureShape> inputs,
            boolean ec2Query) {
        List<ErlClause> clauses = new ArrayList<>();
        for (StructureShape input : inputs) {
            clauses.add(buildFlattenInputClause(model, httpIndex, sp, input, ec2Query));
        }
        return ErlFunction.function("flatten_query_input", 1, clauses);
    }

    private static ErlClause buildFlattenInputClause(
            Model model,
            HttpBindingIndex httpIndex,
            SymbolProvider sp,
            StructureShape input,
            boolean ec2Query) {
        String inputRecord = recordName(sp.toSymbol(input));
        List<MemberShape> members = documentMembers(httpIndex, input);

        List<ErlRecordFieldPattern> fieldPatterns = new ArrayList<>();
        for (MemberShape member : members) {
            String field = memberFieldName(sp, member);
            fieldPatterns.add(ErlRecordFieldPattern.fieldPattern(field, ErlVarPattern.varPattern(toBindingVar(field))));
        }
        ErlRecordPattern pattern = new ErlRecordPattern(inputRecord, fieldPatterns);

        ErlExpr body;
        if (members.isEmpty()) {
            body = ErlList.list();
        } else {
            List<ErlExpr> appendArgs = new ArrayList<>();
            for (MemberShape member : members) {
                String field = memberFieldName(sp, member);
                String wireKey = queryFormKey(member, ec2Query);
                appendArgs.add(ErlCallLocal.callLocal(
                        "flatten_member",
                        ErlBinary.binary(wireKey),
                        ErlVar.var(toBindingVar(field))));
            }
            body = ErlCall.call("lists", "append", ErlList.list(appendArgs.toArray(ErlExpr[]::new)));
        }
        return ErlClause.clause(List.of(pattern), body);
    }

    static ErlFunction buildDecodeResponse(
            Model model,
            ServiceShape service,
            OperationShape op,
            SymbolProvider sp,
            boolean ec2Query) {
        String opName = sp.toSymbol(op).getName();
        StructureShape output = model.expectShape(op.getOutputShape(), StructureShape.class);
        String outputRecord = recordName(sp.toSymbol(output));
        String outputType = sp.toSymbol(output).getName();
        String resultElement = ec2Query
                ? BeamXmlDecoder.ec2QueryResultElementName(op, service)
                : BeamXmlDecoder.queryResultElementName(op, service);

        ErlFunctionSpec spec = ErlFunctionSpec.functionSpec(
                "decode_" + opName + "_response",
                "#http_response{}",
                "{'ok', " + outputType + "} | {'error', term()}");

        ErlRecordPattern successPattern = ErlRecordPattern.recordPattern(
                "http_response",
                ErlRecordFieldPattern.fieldPattern("status", ErlIntegerPattern.integerPattern(200)),
                ErlRecordFieldPattern.fieldPattern("body", ErlVarPattern.varPattern("Body")));

        ErlRecordPattern fallbackPattern = ErlRecordPattern.recordPattern(
                "http_response",
                ErlRecordFieldPattern.fieldPattern("status", ErlVarPattern.varPattern("Status")),
                ErlRecordFieldPattern.fieldPattern("body", ErlVarPattern.varPattern("Body")));

        List<ErlClause> clauses = List.of(
                ErlClause.clause(
                        List.of(successPattern),
                        buildDecodeSuccessBody(output, outputRecord, resultElement, model, sp, ec2Query)),
                ErlClause.clause(
                        List.of(fallbackPattern),
                        ErlCallLocal.callLocal("decode_query_error", ErlVar.var("Status"), ErlVar.var("Body"))));

        return ErlFunction.functionWithDocAndSpec(
                "decode_" + opName + "_response",
                1,
                ErlFunctionDoc.functionDoc("Decode AWS Query response for " + op.getId() + "."),
                spec,
                clauses);
    }

    private static ErlExpr buildDecodeSuccessBody(
            StructureShape output,
            String outputRecord,
            String resultElement,
            Model model,
            SymbolProvider sp,
            boolean ec2Query) {
        ErlCallLocal unwrap = ErlCallLocal.callLocal(
                "unwrap_query_result", ErlVar.var("Body"), ErlBinary.binary(resultElement));
        if (output.members().isEmpty()) {
            ErlExpr emptyOutput = ErlCapturedBlock.capturedBlock("#" + outputRecord + "{}");
            return ErlCase.caseExpr(
                    unwrap,
                    ErlClause.clause(
                            List.of(ErlTuplePattern.tuplePattern(ErlAtomPattern.atomPattern("ok"), W)),
                            ErlTuple.tuple(ErlAtom.atom("ok"), emptyOutput)),
                    ErlClause.clause(
                            List.of(ErlTuplePattern.tuplePattern(
                                    ErlAtomPattern.atomPattern("error"),
                                    ErlTuplePattern.tuplePattern(ErlAtomPattern.atomPattern("missing_result"), W))),
                            ErlTuple.tuple(ErlAtom.atom("ok"), emptyOutput)),
                    ErlClause.clause(
                            List.of(ErlTuplePattern.tuplePattern(
                                    ErlAtomPattern.atomPattern("error"), ErlVarPattern.varPattern("Reason"))),
                            ErlTuple.tuple(ErlAtom.atom("error"), ErlVar.var("Reason"))));
        }
        List<ErlRecordField> fields = buildOutputRecordFields(model, sp, output, "Result", ec2Query);
        ErlExpr okRecord = ErlTuple.tuple(
                ErlAtom.atom("ok"),
                ErlRecord.record(outputRecord, fields.toArray(ErlRecordField[]::new)));
        return ErlCase.caseExpr(
                unwrap,
                ErlClause.clause(
                        List.of(ErlTuplePattern.tuplePattern(
                                ErlAtomPattern.atomPattern("ok"), ErlVarPattern.varPattern("Result"))),
                        okRecord),
                ErlClause.clause(
                        List.of(ErlTuplePattern.tuplePattern(
                                ErlAtomPattern.atomPattern("error"), ErlVarPattern.varPattern("Reason"))),
                        ErlTuple.tuple(ErlAtom.atom("error"), ErlVar.var("Reason"))));
    }

    static ErlFunction buildServerDecodeRequest(
            Model model,
            OperationShape op,
            SymbolProvider sp,
            boolean ec2Query) {
        String opName = sp.toSymbol(op).getName();
        StructureShape input = model.expectShape(op.getInputShape(), StructureShape.class);
        String inputType = sp.toSymbol(input).getName();

        ErlFunctionSpec spec = ErlFunctionSpec.functionSpec(
                "decode_" + opName + "_request", "#http_request{}", inputType);
        ErlRecordPattern pattern = ErlRecordPattern.recordPattern(
                "http_request",
                ErlRecordFieldPattern.fieldPattern("body", ErlVarPattern.varPattern("Body")));

        List<ErlExpr> body = List.of(
                ErlMatch.match(
                        ErlVarPattern.varPattern("Params"),
                        ErlCallLocal.callLocal("parse_query_params", ErlVar.var("Body"))),
                ErlCallLocal.callLocal(
                        "parse_" + recordName(sp.toSymbol(input)) + "_input", ErlVar.var("Params")));

        return ErlFunction.functionWithDocAndSpec(
                "decode_" + opName + "_request",
                1,
                ErlFunctionDoc.functionDoc("Decode AWS Query server request for " + op.getId() + "."),
                spec,
                List.of(ErlClause.clause(List.of(pattern), ErlExprBlock.block(body.toArray(ErlExpr[]::new)))));
    }

    static ErlFunction buildServerEncodeResponse(
            Model model,
            ServiceShape service,
            OperationShape op,
            SymbolProvider sp,
            boolean ec2Query) {
        String opName = sp.toSymbol(op).getName();
        StructureShape output = model.expectShape(op.getOutputShape(), StructureShape.class);
        String outputRecord = recordName(sp.toSymbol(output));
        String outputType = sp.toSymbol(output).getName();
        String resultElement = ec2Query
                ? BeamXmlDecoder.ec2QueryResultElementName(op, service)
                : BeamXmlDecoder.queryResultElementName(op, service);
        String responseElement = operationWireName(op, service) + "Response";

        ErlFunctionSpec spec = ErlFunctionSpec.functionSpec(
                "encode_" + opName + "_response", outputType, "#http_response{}");
        ErlRecordPattern pattern = outputBindingHead(outputRecord, output, sp);

        ErlFun filterUndefined = ErlFun.fun(ErlClause.clause(
                List.of(W, ErlVarPattern.varPattern("V")),
                ErlOp.op("=/=", ErlVar.var("V"), ErlAtom.atom("undefined"))));

        List<ErlExpr> body = new ArrayList<>();
        body.add(ErlMatch.match(
                ErlVarPattern.varPattern("ResultContent"),
                ErlCall.call(
                        "maps",
                        "filter",
                        filterUndefined,
                        ErlMap.map(buildOutputMapEntries(model, sp, output).toArray(ErlMapEntry[]::new)))));
        if (ec2Query) {
            body.add(ErlMatch.match(
                    ErlVarPattern.varPattern("Body"),
                    ErlCallLocal.callLocal(
                            "encode_xml",
                            ErlMap.map(ErlMapEntry.entry(ErlBinary.binary(resultElement), ErlVar.var("ResultContent"))),
                            ErlCallLocal.callLocal("xml_namespace"))));
        } else {
            body.add(ErlMatch.match(
                    ErlVarPattern.varPattern("Body"),
                    ErlCallLocal.callLocal(
                            "wrap_aws_query_response",
                            ErlBinary.binary(resultElement),
                            ErlVar.var("ResultContent"),
                            ErlBinary.binary(responseElement),
                            ErlCallLocal.callLocal("xml_namespace"))));
        }
        body.add(ErlRecord.record(
                "http_response",
                ErlRecordField.field("status", ErlInteger.integer(200)),
                ErlRecordField.field(
                        "headers",
                        ErlList.list(ErlTuple.tuple(
                                ErlBinary.binary("Content-Type"), ErlBinary.binary("text/xml")))),
                ErlRecordField.field("body", ErlVar.var("Body"))));

        return ErlFunction.functionWithDocAndSpec(
                "encode_" + opName + "_response",
                1,
                ErlFunctionDoc.functionDoc("Encode AWS Query server response for " + op.getId() + "."),
                spec,
                List.of(ErlClause.clause(List.of(pattern), ErlExprBlock.block(body.toArray(ErlExpr[]::new)))));
    }

    static ErlFunction buildParseInputFromForm(
            Model model,
            SymbolProvider sp,
            StructureShape input,
            boolean ec2Query) {
        String inputRecord = recordName(sp.toSymbol(input));
        List<MemberShape> members = new ArrayList<>(input.members());

        List<ErlRecordField> fields = new ArrayList<>();
        for (MemberShape member : members) {
            String field = memberFieldName(sp, member);
            String wireKey = queryFormKey(member, ec2Query);
            Shape target = model.expectShape(member.getTarget());
            ErlExpr valueExpr;
            if (target instanceof ListShape) {
                valueExpr = ec2Query
                        ? ErlCallLocal.callLocal(
                                "form_list_values_ec2", ErlVar.var("Params"), ErlBinary.binary(wireKey))
                        : ErlCallLocal.callLocal(
                                "form_list_values_aws", ErlVar.var("Params"), ErlBinary.binary(wireKey));
            } else {
                valueExpr = ErlCallLocal.callLocal("form_value", ErlVar.var("Params"), ErlBinary.binary(wireKey));
            }
            fields.add(ErlRecordField.field(field, valueExpr));
        }

        return ErlFunction.function(
                "parse_" + inputRecord + "_input",
                1,
                List.of(ErlClause.clause(
                        List.of(ErlVarPattern.varPattern("Params")),
                        ErlRecord.record(inputRecord, fields.toArray(ErlRecordField[]::new)))));
    }

    private static ErlRecordPattern inputBindingHead(
            String alias, String recordName, StructureShape input, SymbolProvider sp) {
        List<ErlRecordFieldPattern> fields = new ArrayList<>();
        for (MemberShape member : input.members()) {
            String field = memberFieldName(sp, member);
            fields.add(ErlRecordFieldPattern.fieldPattern(field, ErlVarPattern.varPattern(toBindingVar(field))));
        }
        return new ErlRecordPattern(recordName, fields, alias);
    }

    private static ErlRecordPattern outputBindingHead(String recordName, StructureShape output, SymbolProvider sp) {
        List<ErlRecordFieldPattern> fields = new ArrayList<>();
        for (MemberShape member : output.members()) {
            String field = memberFieldName(sp, member);
            fields.add(ErlRecordFieldPattern.fieldPattern(field, ErlVarPattern.varPattern(toBindingVar(field))));
        }
        return new ErlRecordPattern(recordName, fields);
    }

    private static List<ErlRecordField> buildOutputRecordFields(
            Model model,
            SymbolProvider sp,
            StructureShape output,
            String resultVar,
            boolean ec2Query) {
        List<ErlRecordField> fields = new ArrayList<>();
        for (MemberShape member : output.members()) {
            String field = memberFieldName(sp, member);
            Shape target = model.expectShape(member.getTarget());
            if (target instanceof ListShape listShape) {
                fields.add(ErlRecordField.field(
                        field, buildDecodeListFieldExpr(model, member, listShape, resultVar, sp, ec2Query)));
            } else if (target instanceof StructureShape nested) {
                String element = BeamXmlDecoder.memberElementName(member);
                String nestedVar = xmlVarForElement(element);
                fields.add(ErlRecordField.field(
                        field,
                        ErlCase.caseExpr(
                                ErlCallLocal.callLocal(
                                        "find_element",
                                        ErlBinary.binary(element),
                                        ErlCallLocal.callLocal("element_content", ErlVar.var(resultVar))),
                                ErlClause.clause(
                                        List.of(ErlAtomPattern.atomPattern("undefined")),
                                        ErlAtom.atom("undefined")),
                                ErlClause.clause(
                                        List.of(ErlVarPattern.varPattern(nestedVar)),
                                        buildDecodeStructureExpr(model, nested, nestedVar, sp, ec2Query)))));
            } else {
                fields.add(ErlRecordField.field(
                        field,
                        ErlCallLocal.callLocal(
                                "xml_child_text",
                                ErlVar.var(resultVar),
                                ErlBinary.binary(BeamXmlDecoder.memberElementName(member)))));
            }
        }
        return fields;
    }

    private static ErlExpr buildDecodeStructureExpr(
            Model model,
            StructureShape structure,
            String xmlVar,
            SymbolProvider sp,
            boolean ec2Query) {
        String recordTag = recordName(sp.toSymbol(structure));
        List<ErlRecordField> fields = new ArrayList<>();
        for (MemberShape member : structure.members()) {
            String field = memberFieldName(sp, member);
            Shape target = model.expectShape(member.getTarget());
            if (target instanceof ListShape listShape) {
                fields.add(ErlRecordField.field(
                        field, buildDecodeListFieldExpr(model, member, listShape, xmlVar, sp, ec2Query)));
            } else if (target instanceof StructureShape nested) {
                String element = BeamXmlDecoder.memberElementName(member);
                String nestedVar = xmlVarForElement(element);
                fields.add(ErlRecordField.field(
                        field,
                        ErlCase.caseExpr(
                                ErlCallLocal.callLocal(
                                        "find_element",
                                        ErlBinary.binary(element),
                                        ErlCallLocal.callLocal("element_content", ErlVar.var(xmlVar))),
                                ErlClause.clause(
                                        List.of(ErlAtomPattern.atomPattern("undefined")),
                                        ErlAtom.atom("undefined")),
                                ErlClause.clause(
                                        List.of(ErlVarPattern.varPattern(nestedVar)),
                                        buildDecodeStructureExpr(model, nested, nestedVar, sp, ec2Query)))));
            } else {
                fields.add(ErlRecordField.field(
                        field,
                        ErlCallLocal.callLocal(
                                "xml_child_text",
                                ErlVar.var(xmlVar),
                                ErlBinary.binary(BeamXmlDecoder.memberElementName(member)))));
            }
        }
        if (fields.isEmpty()) {
            return ErlRecord.record(recordTag);
        }
        return ErlRecord.record(recordTag, fields.toArray(ErlRecordField[]::new));
    }

    private static ErlExpr buildDecodeListFieldExpr(
            Model model,
            MemberShape member,
            ListShape listShape,
            String xmlVar,
            SymbolProvider sp,
            boolean ec2Query) {
        String element = BeamXmlDecoder.memberElementName(member);
        String itemElement = listShape.getMember().hasTrait(XmlNameTrait.class)
                ? BeamXmlDecoder.memberElementName(listShape.getMember())
                : BeamXmlBindingIndex.listItemElementName(member, listShape, model);
        ErlExpr listNameExpr = ErlBinary.binary(element);
        Shape listMember = model.expectShape(listShape.getMember().getTarget());
        if (listMember instanceof StructureShape nested) {
            ErlFun decodeFun = ErlFun.fun(ErlClause.clause(
                    List.of(ErlVarPattern.varPattern("Item")),
                    buildDecodeStructureExpr(model, nested, "Item", sp, ec2Query)));
            return ErlCallLocal.callLocal(
                    "xml_child_struct_list",
                    ErlVar.var(xmlVar),
                    listNameExpr,
                    ErlBinary.binary(itemElement),
                    decodeFun);
        }
        return ErlCallLocal.callLocal(
                "xml_child_list", ErlVar.var(xmlVar), listNameExpr, ErlBinary.binary(itemElement));
    }

    private static List<ErlMapEntry> buildOutputMapEntries(
            Model model, SymbolProvider sp, StructureShape output) {
        List<ErlMapEntry> entries = new ArrayList<>();
        for (MemberShape member : output.members()) {
            String field = memberFieldName(sp, member);
            String element = BeamXmlDecoder.memberElementName(member);
            entries.add(ErlMapEntry.entry(ErlBinary.binary(element), ErlVar.var(toBindingVar(field))));
        }
        return entries;
    }

    private static List<MemberShape> documentMembers(HttpBindingIndex httpIndex, StructureShape structure) {
        return new ArrayList<>(structure.members());
    }

    private static String queryFormKey(MemberShape member, boolean ec2Query) {
        return ec2Query
                ? BeamAwsQueryFormEncoder.ec2QueryFormKey(member)
                : BeamAwsQueryFormEncoder.awsQueryFormKey(member);
    }

    private static String recordName(Symbol symbol) {
        return symbol.getName().replace("()", "");
    }

    private static String toBindingVar(String snakeField) {
        return BeamNameUtils.toCamelCaseVariable(snakeField);
    }

    private static String memberFieldName(SymbolProvider sp, MemberShape member) {
        return sp.toSymbol(member).getProperty("fieldName", String.class).orElseThrow();
    }

    private static String xmlVarForElement(String element) {
        if (element.isEmpty()) {
            return "NestedXml";
        }
        return Character.toUpperCase(element.charAt(0)) + element.substring(1) + "Xml";
    }

    private static String operationWireName(OperationShape operation, ServiceShape service) {
        return operation.getId().getName(service);
    }
}
