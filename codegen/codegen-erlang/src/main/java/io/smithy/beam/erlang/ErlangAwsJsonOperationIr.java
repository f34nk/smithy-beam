package io.smithy.beam.erlang;

import io.beam.dsl.erlang.AtomExpr;
import io.beam.dsl.erlang.BlockExpr;
import io.beam.dsl.erlang.Edoc;
import io.beam.dsl.erlang.Function;
import io.beam.dsl.erlang.FunctionClause;
import io.beam.dsl.erlang.IntegerExpr;
import io.beam.dsl.erlang.IntegerPattern;
import io.beam.dsl.erlang.ListExpr;
import io.beam.dsl.erlang.LocalCallExpr;
import io.beam.dsl.erlang.MatchExpr;
import io.beam.dsl.erlang.RecordExpr;
import io.beam.dsl.erlang.RecordField;
import io.beam.dsl.erlang.RecordPattern;
import io.beam.dsl.erlang.RecordPatternField;
import io.beam.dsl.erlang.RemoteCallExpr;
import io.beam.dsl.erlang.Spec;
import io.beam.dsl.erlang.TupleExpr;
import io.beam.dsl.erlang.Variable;
import io.beam.dsl.erlang.VariablePattern;
import io.smithy.beam.core.BeamNameUtils;
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

  static Function buildEncodeRequest(
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

    RecordPattern inputPattern =
        ErlangRestJsonOperationIr.memberBindingHead("Input", inputRecord, input, sp);

    List<io.beam.dsl.erlang.Expression> body = new ArrayList<>();
    body.addAll(
        ErlangRestJsonOperationIr.buildDocumentBodyEncodeExprs(
            model, httpIndex, sp, members, eventStreamModule));
    body.add(
        RecordExpr.of(
            "http_request",
            List.of(
                RecordField.of("method", io.beam.dsl.erlang.BinaryExpr.of("POST")),
                RecordField.of("path", io.beam.dsl.erlang.BinaryExpr.of("/")),
                RecordField.of("query", io.beam.dsl.erlang.MapExpr.of(List.of())),
                RecordField.of(
                    "headers",
                    ListExpr.of(
                        List.of(
                            TupleExpr.of(
                                List.of(
                                    io.beam.dsl.erlang.BinaryExpr.of("Content-Type"),
                                    io.beam.dsl.erlang.BinaryExpr.of(contentType))),
                            TupleExpr.of(
                                List.of(
                                    io.beam.dsl.erlang.BinaryExpr.of("X-Amz-Target"),
                                    io.beam.dsl.erlang.BinaryExpr.of(amzTarget)))))),
                RecordField.of("body", Variable.of("Body")))));

    return Function.of(
        "encode_" + opName + "_request",
        List.of(FunctionClause.of(List.of(inputPattern), BlockExpr.commaSeparated(body, false))),
        Spec.of("encode_" + opName + "_request(" + inputType + ") -> #http_request{}"),
        Edoc.of("Encode AWS JSON request for " + op.getId() + "."));
  }

  static Function buildDecodeResponse(
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

    RecordPattern successPattern =
        RecordPattern.of(
            "http_response",
            List.of(
                RecordPatternField.of("status", IntegerPattern.of(200)),
                RecordPatternField.of("body", VariablePattern.of("Body"))));

    io.beam.dsl.erlang.Expression successBody;
    if (ErlangJsonCodecSupport.isEventStreamPayload(members, model)) {
      MemberShape member = members.get(0);
      UnionShape union = model.expectShape(member.getTarget(), UnionShape.class);
      String helper = ErlangEventStreamEmitter.helperName(sp, union);
      String fieldName = BeamNameUtils.toSnakeCase(member.getMemberName());
      successBody =
          TupleExpr.of(
              List.of(
                  AtomExpr.of("ok"),
                  RecordExpr.of(
                      outputRecord,
                      List.of(
                          RecordField.of(
                              fieldName,
                              RemoteCallExpr.of(
                                  eventStreamModule,
                                  "decode_" + helper,
                                  List.of(Variable.of("Body"))))))));
    } else {
      successBody =
          BlockExpr.commaSeparated(
              List.of(
                  MatchExpr.bindValue("Decoded", ErlangRestJsonOperationIr.decodeBodyJsonExpr()),
                  TupleExpr.of(
                      List.of(
                          AtomExpr.of("ok"),
                          ErlangRestJsonOperationIr.buildDocumentRecordFromDecoded(
                              outputRecord, model, httpIndex, sp, members, eventStreamModule)))),
              false);
    }

    FunctionClause successClause = FunctionClause.of(List.of(successPattern), successBody);
    FunctionClause errorClause =
        FunctionClause.of(
            List.of(
                RecordPattern.of(
                    "http_response",
                    List.of(
                        RecordPatternField.of("status", VariablePattern.of("Status")),
                        RecordPatternField.of("headers", VariablePattern.of("RespHeaders")),
                        RecordPatternField.of("body", VariablePattern.of("Body"))))),
            LocalCallExpr.of(
                "decode_" + opName + "_response_error",
                List.of(Variable.of("Status"), Variable.of("RespHeaders"), Variable.of("Body"))));

    return Function.of(
        "decode_" + opName + "_response",
        List.of(successClause, errorClause),
        Spec.of(
            "decode_"
                + opName
                + "_response(#http_response{}) -> {'ok', "
                + outputType
                + "} | {'error', term()}"),
        Edoc.of("Decode AWS JSON response for " + op.getId() + "."));
  }

  static Function buildErrorDispatch(Model model, OperationShape op, SymbolProvider sp) {
    return ErlangRestJsonOperationIr.buildErrorDispatch(model, op, sp);
  }

  static Function buildDecodeRequest(
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

    RecordPattern pattern =
        RecordPattern.of(
            "http_request", List.of(RecordPatternField.of("body", VariablePattern.of("Body"))));

    io.beam.dsl.erlang.Expression body;
    if (ErlangJsonCodecSupport.isEventStreamPayload(members, model)) {
      body =
          ErlangRestJsonOperationIr.buildDocumentRecordFromDecoded(
              inputRecord, model, httpIndex, sp, members, eventStreamModule);
    } else {
      body =
          BlockExpr.commaSeparated(
              List.of(
                  MatchExpr.bindValue("Decoded", ErlangRestJsonOperationIr.decodeBodyJsonExpr()),
                  ErlangRestJsonOperationIr.buildDocumentRecordFromDecoded(
                      inputRecord, model, httpIndex, sp, members, eventStreamModule)),
              false);
    }

    return Function.of(
        "decode_" + opName + "_request",
        List.of(FunctionClause.of(List.of(pattern), body)),
        Spec.of("decode_" + opName + "_request(#http_request{}) -> " + inputType),
        Edoc.of("Decode AWS JSON request for " + op.getId() + "."));
  }

  static Function buildEncodeResponse(
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

    RecordPattern pattern = ErlangRestJsonOperationIr.outputBindingHead(outputRecord, output, sp);

    List<io.beam.dsl.erlang.Expression> body = new ArrayList<>();
    body.addAll(
        ErlangRestJsonOperationIr.buildDocumentBodyEncodeExprs(
            model, httpIndex, sp, members, eventStreamModule));
    body.add(
        RecordExpr.of(
            "http_response",
            List.of(
                RecordField.of("status", IntegerExpr.of(200)),
                RecordField.of(
                    "headers",
                    ListExpr.of(
                        List.of(
                            TupleExpr.of(
                                List.of(
                                    io.beam.dsl.erlang.BinaryExpr.of("Content-Type"),
                                    io.beam.dsl.erlang.BinaryExpr.of(contentType)))))),
                RecordField.of("body", Variable.of("Body")))));

    return Function.of(
        "encode_" + opName + "_response",
        List.of(FunctionClause.of(List.of(pattern), BlockExpr.commaSeparated(body, false))),
        Spec.of("encode_" + opName + "_response(" + outputType + ") -> #http_response{}"),
        Edoc.of("Encode AWS JSON response for " + op.getId() + "."));
  }
}
