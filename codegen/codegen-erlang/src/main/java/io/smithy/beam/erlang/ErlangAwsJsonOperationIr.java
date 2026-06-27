package io.smithy.beam.erlang;

import io.smithy.beam.core.BeamNameUtils;
import io.smithy.beam.ir.erlang.ErlAtom;
import io.smithy.beam.ir.erlang.ErlBinary;
import io.smithy.beam.ir.erlang.ErlCall;
import io.smithy.beam.ir.erlang.ErlCallLocal;
import io.smithy.beam.ir.erlang.ErlClause;
import io.smithy.beam.ir.erlang.ErlExpr;
import io.smithy.beam.ir.erlang.ErlExprBlock;
import io.smithy.beam.ir.erlang.ErlFunction;
import io.smithy.beam.ir.erlang.ErlFunctionDoc;
import io.smithy.beam.ir.erlang.ErlFunctionSpec;
import io.smithy.beam.ir.erlang.ErlInteger;
import io.smithy.beam.ir.erlang.ErlIntegerPattern;
import io.smithy.beam.ir.erlang.ErlList;
import io.smithy.beam.ir.erlang.ErlMap;
import io.smithy.beam.ir.erlang.ErlMatch;
import io.smithy.beam.ir.erlang.ErlRecord;
import io.smithy.beam.ir.erlang.ErlRecordField;
import io.smithy.beam.ir.erlang.ErlRecordFieldPattern;
import io.smithy.beam.ir.erlang.ErlRecordPattern;
import io.smithy.beam.ir.erlang.ErlTuple;
import io.smithy.beam.ir.erlang.ErlVar;
import io.smithy.beam.ir.erlang.ErlVarPattern;
import java.util.ArrayList;
import java.util.List;
import software.amazon.smithy.codegen.core.SymbolProvider;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.knowledge.HttpBindingIndex;
import software.amazon.smithy.model.shapes.MemberShape;
import software.amazon.smithy.model.shapes.OperationShape;
import software.amazon.smithy.model.shapes.StructureShape;
import software.amazon.smithy.model.shapes.UnionShape;

final class ErlangAwsJsonOperationIr {
  private ErlangAwsJsonOperationIr() {}

  static ErlFunction buildEncodeRequest(
      Model model,
      OperationShape op,
      HttpBindingIndex httpIndex,
      SymbolProvider sp,
      String targetPrefix,
      String contentType,
      String eventStreamModule) {
    String opName = sp.toSymbol(op).getName();
    StructureShape input = model.expectShape(op.getInputShape(), StructureShape.class);
    String inputRecord = ErlangJsonCodecSupport.recordName(sp.toSymbol(input));
    String inputType = sp.toSymbol(input).getName();
    List<MemberShape> members = ErlangJsonCodecSupport.documentMembers(httpIndex, op, input, true);
    String amzTarget = targetPrefix + "." + op.getId().getName();

    ErlFunctionSpec spec =
        ErlFunctionSpec.functionSpec("encode_" + opName + "_request", inputType, "#http_request{}");
    ErlRecordPattern inputPattern =
        ErlangRestJsonOperationIr.memberBindingHead("Input", inputRecord, input, sp);

    List<ErlExpr> body = new ArrayList<>();
    body.addAll(
        ErlangRestJsonOperationIr.buildDocumentBodyEncodeExprs(
            model, httpIndex, sp, members, eventStreamModule));
    body.add(
        ErlRecord.record(
            "http_request",
            ErlRecordField.field("method", ErlBinary.binary("POST")),
            ErlRecordField.field("path", ErlBinary.binary("/")),
            ErlRecordField.field("query", ErlMap.map()),
            ErlRecordField.field(
                "headers",
                ErlList.list(
                    ErlTuple.tuple(ErlBinary.binary("Content-Type"), ErlBinary.binary(contentType)),
                    ErlTuple.tuple(ErlBinary.binary("X-Amz-Target"), ErlBinary.binary(amzTarget)))),
            ErlRecordField.field("body", ErlVar.var("Body"))));

    return ErlFunction.functionWithDocAndSpec(
        "encode_" + opName + "_request",
        1,
        ErlFunctionDoc.functionDoc("Encode AWS JSON request for " + op.getId() + "."),
        spec,
        List.of(
            ErlClause.clause(
                List.of(inputPattern), ErlExprBlock.block(body.toArray(ErlExpr[]::new)))));
  }

  static ErlFunction buildDecodeResponse(
      Model model,
      OperationShape op,
      HttpBindingIndex httpIndex,
      SymbolProvider sp,
      String eventStreamModule) {
    String opName = sp.toSymbol(op).getName();
    StructureShape output = model.expectShape(op.getOutputShape(), StructureShape.class);
    String outputRecord = ErlangJsonCodecSupport.recordName(sp.toSymbol(output));
    String outputType = sp.toSymbol(output).getName();
    List<MemberShape> members =
        ErlangJsonCodecSupport.documentMembers(httpIndex, op, output, false);

    ErlFunctionSpec spec =
        ErlFunctionSpec.functionSpec(
            "decode_" + opName + "_response",
            "#http_response{}",
            "{'ok', " + outputType + "} | {'error', term()}");

    ErlRecordPattern successPattern =
        ErlRecordPattern.recordPattern(
            "http_response",
            ErlRecordFieldPattern.fieldPattern("status", ErlIntegerPattern.integerPattern(200)),
            ErlRecordFieldPattern.fieldPattern("body", ErlVarPattern.varPattern("Body")));

    ErlExpr successBody;
    if (ErlangJsonCodecSupport.isEventStreamPayload(members, model)) {
      MemberShape member = members.get(0);
      UnionShape union = model.expectShape(member.getTarget(), UnionShape.class);
      String helper = ErlangEventStreamEmitter.helperName(sp, union);
      String fieldName = BeamNameUtils.toSnakeCase(member.getMemberName());
      successBody =
          ErlTuple.tuple(
              ErlAtom.atom("ok"),
              ErlRecord.record(
                  outputRecord,
                  ErlRecordField.field(
                      fieldName,
                      ErlCall.call(eventStreamModule, "decode_" + helper, ErlVar.var("Body")))));
    } else {
      successBody =
          ErlExprBlock.block(
              ErlMatch.match(
                  ErlVarPattern.varPattern("Decoded"),
                  ErlangRestJsonOperationIr.decodeBodyJsonExpr()),
              ErlTuple.tuple(
                  ErlAtom.atom("ok"),
                  ErlangRestJsonOperationIr.buildDocumentRecordFromDecoded(
                      outputRecord, model, httpIndex, sp, members, eventStreamModule)));
    }

    ErlClause successClause = ErlClause.clause(List.of(successPattern), successBody);
    ErlClause errorClause =
        ErlClause.clause(
            List.of(
                ErlRecordPattern.recordPattern(
                    "http_response",
                    ErlRecordFieldPattern.fieldPattern(
                        "status", ErlVarPattern.varPattern("Status")),
                    ErlRecordFieldPattern.fieldPattern(
                        "headers", ErlVarPattern.varPattern("RespHeaders")),
                    ErlRecordFieldPattern.fieldPattern("body", ErlVarPattern.varPattern("Body")))),
            ErlCallLocal.callLocal(
                "decode_" + opName + "_response_error",
                ErlVar.var("Status"),
                ErlVar.var("RespHeaders"),
                ErlVar.var("Body")));

    return ErlFunction.functionWithDocAndSpec(
        "decode_" + opName + "_response",
        1,
        ErlFunctionDoc.functionDoc("Decode AWS JSON response for " + op.getId() + "."),
        spec,
        List.of(successClause, errorClause));
  }

  static ErlFunction buildErrorDispatch(Model model, OperationShape op, SymbolProvider sp) {
    return ErlangRestJsonOperationIr.buildErrorDispatch(model, op, sp);
  }

  static ErlFunction buildDecodeRequest(
      Model model,
      OperationShape op,
      HttpBindingIndex httpIndex,
      SymbolProvider sp,
      String eventStreamModule) {
    String opName = sp.toSymbol(op).getName();
    StructureShape input = model.expectShape(op.getInputShape(), StructureShape.class);
    String inputRecord = ErlangJsonCodecSupport.recordName(sp.toSymbol(input));
    String inputType = sp.toSymbol(input).getName();
    List<MemberShape> members = ErlangJsonCodecSupport.documentMembers(httpIndex, op, input, true);

    ErlFunctionSpec spec =
        ErlFunctionSpec.functionSpec("decode_" + opName + "_request", "#http_request{}", inputType);
    ErlRecordPattern pattern =
        ErlRecordPattern.recordPattern(
            "http_request",
            ErlRecordFieldPattern.fieldPattern("body", ErlVarPattern.varPattern("Body")));

    ErlExpr body;
    if (ErlangJsonCodecSupport.isEventStreamPayload(members, model)) {
      body =
          ErlangRestJsonOperationIr.buildDocumentRecordFromDecoded(
              inputRecord, model, httpIndex, sp, members, eventStreamModule);
    } else {
      body =
          ErlExprBlock.block(
              ErlMatch.match(
                  ErlVarPattern.varPattern("Decoded"),
                  ErlangRestJsonOperationIr.decodeBodyJsonExpr()),
              ErlangRestJsonOperationIr.buildDocumentRecordFromDecoded(
                  inputRecord, model, httpIndex, sp, members, eventStreamModule));
    }

    return ErlFunction.functionWithDocAndSpec(
        "decode_" + opName + "_request",
        1,
        ErlFunctionDoc.functionDoc("Decode AWS JSON request for " + op.getId() + "."),
        spec,
        List.of(ErlClause.clause(List.of(pattern), body)));
  }

  static ErlFunction buildEncodeResponse(
      Model model,
      OperationShape op,
      HttpBindingIndex httpIndex,
      SymbolProvider sp,
      String contentType,
      String eventStreamModule) {
    String opName = sp.toSymbol(op).getName();
    StructureShape output = model.expectShape(op.getOutputShape(), StructureShape.class);
    String outputRecord = ErlangJsonCodecSupport.recordName(sp.toSymbol(output));
    String outputType = sp.toSymbol(output).getName();
    List<MemberShape> members =
        ErlangJsonCodecSupport.documentMembers(httpIndex, op, output, false);

    ErlFunctionSpec spec =
        ErlFunctionSpec.functionSpec(
            "encode_" + opName + "_response", outputType, "#http_response{}");
    ErlRecordPattern pattern =
        ErlangRestJsonOperationIr.outputBindingHead(outputRecord, output, sp);

    List<ErlExpr> body = new ArrayList<>();
    body.addAll(
        ErlangRestJsonOperationIr.buildDocumentBodyEncodeExprs(
            model, httpIndex, sp, members, eventStreamModule));
    body.add(
        ErlRecord.record(
            "http_response",
            ErlRecordField.field("status", ErlInteger.integer(200)),
            ErlRecordField.field(
                "headers",
                ErlList.list(
                    ErlTuple.tuple(
                        ErlBinary.binary("Content-Type"), ErlBinary.binary(contentType)))),
            ErlRecordField.field("body", ErlVar.var("Body"))));

    return ErlFunction.functionWithDocAndSpec(
        "encode_" + opName + "_response",
        1,
        ErlFunctionDoc.functionDoc("Encode AWS JSON response for " + op.getId() + "."),
        spec,
        List.of(
            ErlClause.clause(List.of(pattern), ErlExprBlock.block(body.toArray(ErlExpr[]::new)))));
  }
}
