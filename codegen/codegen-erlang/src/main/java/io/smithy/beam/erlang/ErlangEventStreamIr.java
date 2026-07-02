package io.smithy.beam.erlang;

import io.smithy.beam.core.BeamNameUtils;
import io.smithy.beam.ir.erlang.*;
import java.util.ArrayList;
import java.util.List;
import software.amazon.smithy.codegen.core.Symbol;
import software.amazon.smithy.codegen.core.SymbolProvider;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.shapes.BlobShape;
import software.amazon.smithy.model.shapes.MemberShape;
import software.amazon.smithy.model.shapes.ServiceShape;
import software.amazon.smithy.model.shapes.Shape;
import software.amazon.smithy.model.shapes.StringShape;
import software.amazon.smithy.model.shapes.StructureShape;
import software.amazon.smithy.model.shapes.UnionShape;
import software.amazon.smithy.model.traits.JsonNameTrait;

final class ErlangEventStreamIr {
  private ErlangEventStreamIr() {}

  static ErlModule eventStreamModule(
      String moduleName,
      String typesHeaderFile,
      ServiceShape service,
      List<UnionShape> unions,
      Model model,
      SymbolProvider sp,
      List<String> exports) {
    List<ErlFunction> functions = new ArrayList<>();
    for (UnionShape union : unions) {
      functions.addAll(unionHelpers(model, union, sp));
    }
    functions.add(encodeEventHeaders());
    functions.add(headerValue());

    return new ErlModule(
        moduleName,
        List.of(
            ErlComment.comment(
                "Generated Amazon Event Stream helpers for " + service.getId() + ".")),
        List.of(
            new ErlAttribute("include", "\"" + typesHeaderFile + "\""),
            ErlExportAttribute.export(exports)),
        functions);
  }

  static ErlFunction encodeEventHeaders() {
    return ErlFunction.function(
        "encode_event_headers",
        1,
        List.of(
            ErlClause.clause(
                List.of(ErlVarPattern.varPattern("EventType")),
                ErlList.list(
                    ErlTuple.tuple(ErlBinary.binary(":event-type"), ErlVar.var("EventType")),
                    ErlTuple.tuple(ErlBinary.binary(":message-type"), ErlBinary.binary("event")),
                    ErlTuple.tuple(
                        ErlBinary.binary(":content-type"),
                        ErlBinary.binary("application/json"))))));
  }

  static ErlFunction headerValue() {
    return ErlFunction.function(
        "header_value",
        2,
        List.of(
            ErlClause.clause(
                List.of(ErlVarPattern.varPattern("Headers"), ErlVarPattern.varPattern("Name")),
                ErlCall.call(
                    "proplists",
                    "get_value",
                    ErlVar.var("Name"),
                    ErlVar.var("Headers"),
                    ErlAtom.atom("undefined")))));
  }

  static List<ErlFunction> unionHelpers(Model model, UnionShape union, SymbolProvider sp) {
    return List.of(
        unionEncodeList(union, sp),
        unionDecodeList(union, sp),
        unionEncodeEvent(model, union, sp),
        unionDecodeEvent(union, sp),
        unionDecodeEventType(model, union, sp));
  }

  static ErlFunction unionEncodeList(UnionShape union, SymbolProvider sp) {
    String helper = helperName(sp, union);
    return ErlFunction.function(
        "encode_" + helper,
        1,
        List.of(
            ErlClause.clause(
                List.of(ErlVarPattern.varPattern("Events")),
                List.of(ErlGuard.guard("is_list", ErlVar.var("Events"))),
                ErlListComprehension.comprehension(
                    ErlCallLocal.callLocal("encode_" + helper + "_event", ErlVar.var("E")),
                    ErlVarPattern.varPattern("E"),
                    ErlVar.var("Events")))));
  }

  static ErlFunction unionDecodeList(UnionShape union, SymbolProvider sp) {
    String helper = helperName(sp, union);
    return ErlFunction.function(
        "decode_" + helper,
        1,
        List.of(
            ErlClause.clause(
                List.of(ErlVarPattern.varPattern("Body")),
                List.of(ErlGuard.guard("is_binary", ErlVar.var("Body"))),
                ErlListComprehension.comprehension(
                    ErlCallLocal.callLocal("decode_" + helper + "_event", ErlVar.var("F")),
                    ErlVarPattern.varPattern("F"),
                    ErlRemoteCall.call(
                        ErlAtom.atom("aws_event_stream"), "decode_frames", ErlVar.var("Body"))))));
  }

  static ErlFunction unionEncodeEvent(Model model, UnionShape union, SymbolProvider sp) {
    String helper = helperName(sp, union);
    List<ErlClause> clauses = new ArrayList<>();
    for (MemberShape member : union.members()) {
      clauses.add(encodeEventClause(model, helper, member, sp));
    }
    clauses.add(
        ErlClause.clause(
            List.of(
                ErlTuplePattern.tuplePattern(
                    ErlAtomPattern.atomPattern("unknown"), ErlVarPattern.varPattern("_"))),
            ErlCallLocal.callLocal(
                "error", ErlTuple.tuple(ErlAtom.atom("bad_event"), ErlAtom.atom("unknown")))));
    return ErlFunction.function("encode_" + helper + "_event", 1, clauses);
  }

  static ErlFunction unionDecodeEvent(UnionShape union, SymbolProvider sp) {
    String helper = helperName(sp, union);
    return ErlFunction.function(
        "decode_" + helper + "_event",
        1,
        List.of(
            ErlClause.blockClause(
                List.of(
                    ErlMapPattern.mapPattern(
                        ErlMapFieldPattern.fieldPattern(
                            "headers", ErlVarPattern.varPattern("Headers")),
                        ErlMapFieldPattern.fieldPattern(
                            "payload", ErlVarPattern.varPattern("Payload")))),
                ErlExprBlock.block(
                    ErlMatch.match(
                        ErlVarPattern.varPattern("EventType"),
                        ErlCallLocal.callLocal(
                            "header_value",
                            ErlVar.var("Headers"),
                            ErlBinary.binary(":event-type"))),
                    ErlCallLocal.callLocal(
                        "decode_" + helper + "_event_type",
                        ErlVar.var("EventType"),
                        ErlVar.var("Payload"))))));
  }

  static ErlFunction unionDecodeEventType(Model model, UnionShape union, SymbolProvider sp) {
    String helper = helperName(sp, union);
    List<ErlClause> clauses = new ArrayList<>();
    for (MemberShape member : union.members()) {
      clauses.add(decodeEventTypeClause(model, helper, member, sp));
    }
    clauses.add(
        ErlClause.clause(
            List.of(ErlVarPattern.varPattern("EventType"), ErlVarPattern.varPattern("_Payload")),
            ErlCallLocal.callLocal(
                "error", ErlTuple.tuple(ErlAtom.atom("bad_event"), ErlVar.var("EventType")))));
    return ErlFunction.function("decode_" + helper + "_event_type", 2, clauses);
  }

  static String helperName(SymbolProvider sp, UnionShape union) {
    return sp.toSymbol(union).getName().replace("()", "");
  }

  private static ErlClause encodeEventClause(
      Model model, String helper, MemberShape member, SymbolProvider sp) {
    String tag = ErlangUnionHelperIr.unionTagForMember(sp, member);
    String eventType = member.getMemberName();
    Shape target = model.expectShape(member.getTarget());
    return ErlClause.blockClause(
        List.of(
            ErlTuplePattern.tuplePattern(
                ErlAtomPattern.atomPattern(tag), ErlVarPattern.varPattern("Value"))),
        ErlExprBlock.block(
            ErlMatch.match(
                ErlVarPattern.varPattern("Payload"),
                encodeMemberPayload(model, target, "Value", sp)),
            ErlMatch.match(
                ErlVarPattern.varPattern("Headers"),
                ErlCallLocal.callLocal("encode_event_headers", ErlBinary.binary(eventType))),
            ErlRemoteCall.call(
                ErlAtom.atom("aws_event_stream"),
                "frame",
                ErlVar.var("Headers"),
                ErlVar.var("Payload"))));
  }

  private static ErlClause decodeEventTypeClause(
      Model model, String helper, MemberShape member, SymbolProvider sp) {
    String tag = ErlangUnionHelperIr.unionTagForMember(sp, member);
    String eventType = member.getMemberName();
    Shape target = model.expectShape(member.getTarget());
    return ErlClause.clause(
        List.of(ErlBinaryPattern.binaryPattern(eventType), ErlVarPattern.varPattern("Payload")),
        ErlTuple.tuple(ErlAtom.atom(tag), decodeMemberPayload(model, target, "Payload", sp)));
  }

  private static ErlExpr encodeMemberPayload(
      Model model, Shape target, String valueVar, SymbolProvider sp) {
    if (target instanceof StructureShape structure) {
      return encodeStructurePayload(structure, valueVar, sp);
    }
    if (target instanceof BlobShape || target instanceof StringShape) {
      return ErlVar.var(valueVar);
    }
    return ErlCall.call("jsone", "encode", ErlVar.var(valueVar));
  }

  private static ErlExpr decodeMemberPayload(
      Model model, Shape target, String payloadVar, SymbolProvider sp) {
    if (target instanceof StructureShape structure) {
      return decodeStructurePayload(structure, payloadVar, sp);
    }
    if (target instanceof BlobShape || target instanceof StringShape) {
      return ErlVar.var(payloadVar);
    }
    return ErlCall.call("jsone", "decode", ErlVar.var(payloadVar));
  }

  private static ErlExpr encodeStructurePayload(
      StructureShape structure, String valueVar, SymbolProvider sp) {
    String recordName = recordName(sp.toSymbol(structure));
    if (structure.members().isEmpty()) {
      return ErlCall.call("jsone", "encode", ErlMap.map());
    }
    List<ErlMapEntry> entries = new ArrayList<>();
    for (MemberShape member : structure.members()) {
      String wireKey = jsonKey(member);
      String fieldName = BeamNameUtils.toSnakeCase(member.getMemberName());
      entries.add(
          ErlMapEntry.entry(
              ErlBinary.binary(wireKey),
              ErlRecordAccess.recordAccess(ErlVar.var(valueVar), recordName, fieldName)));
    }
    return ErlCall.call(
        "jsone",
        "encode",
        ErlCall.call(
            "maps",
            "filter",
            ErlFun.fun(
                ErlClause.clause(
                    List.of(ErlVarPattern.varPattern("_"), ErlVarPattern.varPattern("V")),
                    ErlOp.op("=/=", ErlVar.var("V"), ErlAtom.atom("undefined")))),
            ErlMap.map(entries.toArray(ErlMapEntry[]::new))));
  }

  private static ErlExpr decodeStructurePayload(
      StructureShape structure, String payloadVar, SymbolProvider sp) {
    String recordName = recordName(sp.toSymbol(structure));
    if (structure.members().isEmpty()) {
      return ErlRecord.record(recordName);
    }
    ErlCall decoded =
        ErlCall.call("jsone", "decode", ErlVar.var(payloadVar));
    List<ErlRecordField> fields = new ArrayList<>();
    for (MemberShape member : structure.members()) {
      String wireKey = jsonKey(member);
      String fieldName = BeamNameUtils.toSnakeCase(member.getMemberName());
      fields.add(
          ErlRecordField.field(
              fieldName,
              ErlCall.call(
                  "maps", "get", ErlBinary.binary(wireKey), decoded, ErlAtom.atom("undefined"))));
    }
    return ErlRecord.record(recordName, fields.toArray(ErlRecordField[]::new));
  }

  private static String recordName(Symbol symbol) {
    return symbol.getName().replace("()", "");
  }

  private static String jsonKey(MemberShape member) {
    return member
        .getTrait(JsonNameTrait.class)
        .map(JsonNameTrait::getValue)
        .orElse(member.getMemberName());
  }
}
