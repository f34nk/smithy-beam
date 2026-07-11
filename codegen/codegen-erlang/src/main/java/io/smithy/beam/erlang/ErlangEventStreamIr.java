package io.smithy.beam.erlang;

import io.beam.ir.erlang.AtomExpr;
import io.beam.ir.erlang.AtomPattern;
import io.beam.ir.erlang.BinaryExpr;
import io.beam.ir.erlang.BinaryPattern;
import io.beam.ir.erlang.BlockExpr;
import io.beam.ir.erlang.Expression;
import io.beam.ir.erlang.Fun;
import io.beam.ir.erlang.FunClause;
import io.beam.ir.erlang.Function;
import io.beam.ir.erlang.FunctionClause;
import io.beam.ir.erlang.InfixExpr;
import io.beam.ir.erlang.IsTypeGuard;
import io.beam.ir.erlang.ListComprehensionExpr;
import io.beam.ir.erlang.ListExpr;
import io.beam.ir.erlang.LocalCallExpr;
import io.beam.ir.erlang.MapEntry;
import io.beam.ir.erlang.MapExpr;
import io.beam.ir.erlang.MapPattern;
import io.beam.ir.erlang.MapPatternEntry;
import io.beam.ir.erlang.MatchExpr;
import io.beam.ir.erlang.Module;
import io.beam.ir.erlang.RecordExpr;
import io.beam.ir.erlang.RecordField;
import io.beam.ir.erlang.RecordFieldAccessExpr;
import io.beam.ir.erlang.RemoteCallExpr;
import io.beam.ir.erlang.TupleExpr;
import io.beam.ir.erlang.TuplePattern;
import io.beam.ir.erlang.Variable;
import io.beam.ir.erlang.VariablePattern;
import io.smithy.beam.core.BeamNameUtils;
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

  static Module eventStreamModule(
      String moduleName,
      String typesHeaderFile,
      ServiceShape service,
      List<UnionShape> unions,
      Model model,
      SymbolProvider sp,
      List<String> exports) {
    List<Function> functions = new ArrayList<>();
    for (UnionShape union : unions) {
      functions.addAll(unionHelpers(model, union, sp));
    }
    functions.add(encodeEventHeaders());
    functions.add(headerValue());
    return Module.of(
        moduleName,
        functions,
        List.of("Generated Amazon Event Stream helpers for " + service.getId() + "."),
        null,
        List.of(typesHeaderFile),
        null,
        exports);
  }

  static Function encodeEventHeaders() {
    return Function.of(
        "encode_event_headers",
        List.of(
            FunctionClause.of(
                List.of(VariablePattern.of("EventType")),
                ListExpr.of(
                    List.of(
                        TupleExpr.of(
                            List.of(BinaryExpr.of(":event-type"), Variable.of("EventType"))),
                        TupleExpr.of(
                            List.of(BinaryExpr.of(":message-type"), BinaryExpr.of("event"))),
                        TupleExpr.of(
                            List.of(
                                BinaryExpr.of(":content-type"),
                                BinaryExpr.of("application/json"))))))));
  }

  static Function headerValue() {
    return Function.of(
        "header_value",
        List.of(
            FunctionClause.of(
                List.of(VariablePattern.of("Headers"), VariablePattern.of("Name")),
                RemoteCallExpr.of(
                    "proplists",
                    "get_value",
                    List.of(
                        Variable.of("Name"), Variable.of("Headers"), AtomExpr.of("undefined"))))));
  }

  static List<Function> unionHelpers(Model model, UnionShape union, SymbolProvider sp) {
    return List.of(
        unionEncodeList(union, sp),
        unionDecodeList(union, sp),
        unionEncodeEvent(model, union, sp),
        unionDecodeEvent(union, sp),
        unionDecodeEventType(model, union, sp));
  }

  static Function unionEncodeList(UnionShape union, SymbolProvider sp) {
    String helper = helperName(sp, union);
    return Function.of(
        "encode_" + helper,
        List.of(
            FunctionClause.of(
                List.of(VariablePattern.of("Events")),
                IsTypeGuard.of("list", Variable.of("Events")),
                ListComprehensionExpr.of(
                    LocalCallExpr.of("encode_" + helper + "_event", List.of(Variable.of("E"))),
                    VariablePattern.of("E"),
                    Variable.of("Events")))));
  }

  static Function unionDecodeList(UnionShape union, SymbolProvider sp) {
    String helper = helperName(sp, union);
    return Function.of(
        "decode_" + helper,
        List.of(
            FunctionClause.of(
                List.of(VariablePattern.of("Body")),
                IsTypeGuard.of("binary", Variable.of("Body")),
                ListComprehensionExpr.of(
                    LocalCallExpr.of("decode_" + helper + "_event", List.of(Variable.of("F"))),
                    VariablePattern.of("F"),
                    RemoteCallExpr.of(
                        "aws_event_stream", "decode_frames", List.of(Variable.of("Body")))))));
  }

  static Function unionEncodeEvent(Model model, UnionShape union, SymbolProvider sp) {
    String helper = helperName(sp, union);
    List<FunctionClause> clauses = new ArrayList<>();
    for (MemberShape member : union.members()) {
      clauses.add(encodeEventClause(model, helper, member, sp));
    }
    clauses.add(
        FunctionClause.of(
            List.of(TuplePattern.of(List.of(AtomPattern.of("unknown"), VariablePattern.of("_")))),
            LocalCallExpr.of(
                "error",
                List.of(TupleExpr.of(List.of(AtomExpr.of("bad_event"), AtomExpr.of("unknown")))))));
    return Function.of("encode_" + helper + "_event", clauses);
  }

  static Function unionDecodeEvent(UnionShape union, SymbolProvider sp) {
    String helper = helperName(sp, union);
    return Function.of(
        "decode_" + helper + "_event",
        List.of(
            FunctionClause.of(
                List.of(
                    MapPattern.of(
                        List.of(
                            MapPatternEntry.of(
                                AtomExpr.of("headers"), VariablePattern.of("Headers"), true),
                            MapPatternEntry.of(
                                AtomExpr.of("payload"), VariablePattern.of("Payload"), true)))),
                BlockExpr.commaSeparated(
                    List.of(
                        MatchExpr.bindValue(
                            "EventType",
                            LocalCallExpr.of(
                                "header_value",
                                List.of(Variable.of("Headers"), BinaryExpr.of(":event-type")))),
                        LocalCallExpr.of(
                            "decode_" + helper + "_event_type",
                            List.of(Variable.of("EventType"), Variable.of("Payload")))),
                    false))));
  }

  static Function unionDecodeEventType(Model model, UnionShape union, SymbolProvider sp) {
    String helper = helperName(sp, union);
    List<FunctionClause> clauses = new ArrayList<>();
    for (MemberShape member : union.members()) {
      clauses.add(decodeEventTypeClause(model, helper, member, sp));
    }
    clauses.add(
        FunctionClause.of(
            List.of(VariablePattern.of("EventType"), VariablePattern.of("_Payload")),
            LocalCallExpr.of(
                "error",
                List.of(
                    TupleExpr.of(List.of(AtomExpr.of("bad_event"), Variable.of("EventType")))))));
    return Function.of("decode_" + helper + "_event_type", clauses);
  }

  static String helperName(SymbolProvider sp, UnionShape union) {
    return sp.toSymbol(union).getName().replace("()", "");
  }

  private static FunctionClause encodeEventClause(
      Model model, String helper, MemberShape member, SymbolProvider sp) {
    String tag = ErlangUnionHelperIr.unionTagForMember(sp, member);
    String eventType = member.getMemberName();
    Shape target = model.expectShape(member.getTarget());
    return FunctionClause.of(
        List.of(TuplePattern.of(List.of(AtomPattern.of(tag), VariablePattern.of("Value")))),
        BlockExpr.commaSeparated(
            List.of(
                MatchExpr.bindValue("Payload", encodeMemberPayload(model, target, "Value", sp)),
                MatchExpr.bindValue(
                    "Headers",
                    LocalCallExpr.of("encode_event_headers", List.of(BinaryExpr.of(eventType)))),
                RemoteCallExpr.of(
                    "aws_event_stream",
                    "frame",
                    List.of(Variable.of("Headers"), Variable.of("Payload")))),
            false));
  }

  private static FunctionClause decodeEventTypeClause(
      Model model, String helper, MemberShape member, SymbolProvider sp) {
    String tag = ErlangUnionHelperIr.unionTagForMember(sp, member);
    String eventType = member.getMemberName();
    Shape target = model.expectShape(member.getTarget());
    return FunctionClause.of(
        List.of(BinaryPattern.of(eventType), VariablePattern.of("Payload")),
        TupleExpr.of(List.of(AtomExpr.of(tag), decodeMemberPayload(model, target, "Payload", sp))));
  }

  private static Expression encodeMemberPayload(
      Model model, Shape target, String valueVar, SymbolProvider sp) {
    if (target instanceof StructureShape structure) {
      return encodeStructurePayload(structure, valueVar, sp);
    }
    if (target instanceof BlobShape || target instanceof StringShape) {
      return Variable.of(valueVar);
    }
    return RemoteCallExpr.of("jsone", "encode", List.of(Variable.of(valueVar)));
  }

  private static Expression decodeMemberPayload(
      Model model, Shape target, String payloadVar, SymbolProvider sp) {
    if (target instanceof StructureShape structure) {
      return decodeStructurePayload(structure, payloadVar, sp);
    }
    if (target instanceof BlobShape || target instanceof StringShape) {
      return Variable.of(payloadVar);
    }
    return RemoteCallExpr.of("jsone", "decode", List.of(Variable.of(payloadVar)));
  }

  private static Expression encodeStructurePayload(
      StructureShape structure, String valueVar, SymbolProvider sp) {
    String recordName = recordName(sp.toSymbol(structure));
    if (structure.members().isEmpty()) {
      return RemoteCallExpr.of("jsone", "encode", List.of(MapExpr.of(List.of())));
    }
    List<MapEntry> entries = new ArrayList<>();
    for (MemberShape member : structure.members()) {
      String wireKey = jsonKey(member);
      String fieldName = BeamNameUtils.toSnakeCase(member.getMemberName());
      entries.add(
          MapEntry.of(
              BinaryExpr.of(wireKey),
              RecordFieldAccessExpr.of(Variable.of(valueVar), recordName, fieldName)));
    }
    return RemoteCallExpr.of(
        "jsone",
        "encode",
        List.of(
            RemoteCallExpr.of(
                "maps",
                "filter",
                List.of(
                    Fun.of(
                        List.of(
                            FunClause.of(
                                List.of(VariablePattern.of("_"), VariablePattern.of("V")),
                                InfixExpr.of(Variable.of("V"), "=/=", AtomExpr.of("undefined"))))),
                    MapExpr.of(entries)))));
  }

  private static Expression decodeStructurePayload(
      StructureShape structure, String payloadVar, SymbolProvider sp) {
    String recordName = recordName(sp.toSymbol(structure));
    if (structure.members().isEmpty()) {
      return RecordExpr.of(recordName, List.of());
    }
    List<RecordField> fields = new ArrayList<>();
    Expression decodedPayload =
        RemoteCallExpr.of("jsone", "decode", List.of(Variable.of(payloadVar)));
    for (MemberShape member : structure.members()) {
      String wireKey = jsonKey(member);
      String fieldName = BeamNameUtils.toSnakeCase(member.getMemberName());
      fields.add(
          RecordField.of(
              fieldName,
              RemoteCallExpr.of(
                  "maps",
                  "get",
                  List.of(BinaryExpr.of(wireKey), decodedPayload, AtomExpr.of("undefined")))));
    }
    return RecordExpr.of(recordName, fields);
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
