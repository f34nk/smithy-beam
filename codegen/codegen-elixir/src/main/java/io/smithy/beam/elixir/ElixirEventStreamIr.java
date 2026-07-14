package io.smithy.beam.elixir;

import io.smithy.beam.core.BeamElixirLayout;
import io.smithy.beam.core.BeamEventStreamIndex;
import io.smithy.beam.core.BeamNameUtils;
import io.smithy.beam.ir.elixir.ExAliasAttr;
import io.smithy.beam.ir.elixir.ExAtom;
import io.smithy.beam.ir.elixir.ExAtomPattern;
import io.smithy.beam.ir.elixir.ExCall;
import io.smithy.beam.ir.elixir.ExCallLocal;
import io.smithy.beam.ir.elixir.ExCapturedBlock;
import io.smithy.beam.ir.elixir.ExClause;
import io.smithy.beam.ir.elixir.ExDoc;
import io.smithy.beam.ir.elixir.ExExpr;
import io.smithy.beam.ir.elixir.ExExprBlock;
import io.smithy.beam.ir.elixir.ExFunction;
import io.smithy.beam.ir.elixir.ExGuard;
import io.smithy.beam.ir.elixir.ExMap;
import io.smithy.beam.ir.elixir.ExMapEntry;
import io.smithy.beam.ir.elixir.ExMapFieldPattern;
import io.smithy.beam.ir.elixir.ExMapPattern;
import io.smithy.beam.ir.elixir.ExMatch;
import io.smithy.beam.ir.elixir.ExModule;
import io.smithy.beam.ir.elixir.ExModuledoc;
import io.smithy.beam.ir.elixir.ExPipeline;
import io.smithy.beam.ir.elixir.ExString;
import io.smithy.beam.ir.elixir.ExStringPattern;
import io.smithy.beam.ir.elixir.ExStruct;
import io.smithy.beam.ir.elixir.ExTuple;
import io.smithy.beam.ir.elixir.ExTuplePattern;
import io.smithy.beam.ir.elixir.ExVar;
import io.smithy.beam.ir.elixir.ExVarPattern;
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

final class ElixirEventStreamIr {
  private ElixirEventStreamIr() {}

  static ExModule eventStreamModule(ElixirContext ctx, ServiceShape service) {
    BeamElixirLayout layout =
        new BeamElixirLayout(ctx.settings(), service.getId().getNamespace(), service);
    String moduleName = ElixirSymbolProvider.toModuleName(layout.eventStreamModuleName());
    String typesMod = ElixirSymbolProvider.toModuleName(layout.typesModuleName());
    BeamEventStreamIndex index = BeamEventStreamIndex.of(ctx.model());
    List<UnionShape> unions = index.eventStreamUnions(service);
    SymbolProvider sp = ctx.symbolProvider();
    Model model = ctx.model();

    List<ExFunction> functions = new ArrayList<>();
    for (UnionShape union : unions) {
      functions.addAll(unionHelpers(model, union, sp, typesMod));
    }

    return ExModule.module(
        moduleName,
        List.of(
            ExModuledoc.moduledoc(
                "Generated Amazon Event Stream helpers for " + service.getId() + " (generated).")),
        List.of(ExAliasAttr.alias(typesMod)),
        functions);
  }

  static List<ExFunction> unionHelpers(
      Model model, UnionShape union, SymbolProvider sp, String typesMod) {
    return List.of(
        unionEncodeList(union, sp),
        unionDecodeList(union, sp),
        unionEncodeEvent(model, union, sp, typesMod),
        unionDecodeEvent(union, sp),
        unionDecodeEventType(model, union, sp, typesMod));
  }

  static ExFunction unionEncodeList(UnionShape union, SymbolProvider sp) {
    String helper = helperName(sp, union);
    return ExFunction.functionWithDocAndSpec(
        "def",
        "encode_" + helper,
        ExDoc.doc("Encodes a list of event stream events into framed binaries."),
        null,
        List.of(
            ExClause.blockClause(
                List.of(ExVarPattern.var("events")),
                List.of(ExGuard.guard("is_list", ExVar.var("events"))),
                ExCall.call(
                    "Enum",
                    "map",
                    ExVar.var("events"),
                    ExCapturedBlock.capturedBlock("&encode_" + helper + "_event/1")))));
  }

  static ExFunction unionDecodeList(UnionShape union, SymbolProvider sp) {
    String helper = helperName(sp, union);
    return ExFunction.functionWithDocAndSpec(
        "def",
        "decode_" + helper,
        ExDoc.doc("Decodes an event stream body into tagged events."),
        null,
        List.of(
            ExClause.blockClause(
                List.of(ExVarPattern.var("body")),
                List.of(ExGuard.guard("is_binary", ExVar.var("body"))),
                ExPipeline.pipeChain(
                    ExVar.var("body"),
                    ExCapturedBlock.capturedBlock("AwsEventStream.decode_frames()"),
                    ExCapturedBlock.capturedBlock("Enum.map(&decode_" + helper + "_event/1)")))));
  }

  static ExFunction unionEncodeEvent(
      Model model, UnionShape union, SymbolProvider sp, String typesMod) {
    String helper = helperName(sp, union);
    List<ExClause> clauses = new ArrayList<>();
    for (MemberShape member : union.members()) {
      clauses.add(encodeEventClause(model, helper, member, sp, typesMod));
    }
    clauses.add(
        ExClause.inlineClause(
            List.of(ExTuplePattern.tuple(ExAtomPattern.atom("unknown"), ExVarPattern.var("_"))),
            ExCapturedBlock.capturedBlock("raise ArgumentError, \"unknown event\"")));
    return ExFunction.defpFunction("encode_" + helper + "_event", clauses);
  }

  static ExFunction unionDecodeEvent(UnionShape union, SymbolProvider sp) {
    String helper = helperName(sp, union);
    return ExFunction.defpFunction(
        "decode_" + helper + "_event",
        List.of(
            ExClause.blockClause(
                List.of(
                    ExMapPattern.map(
                        ExMapFieldPattern.field(
                            ExAtom.atom("headers"), ExVarPattern.var("headers")),
                        ExMapFieldPattern.field(
                            ExAtom.atom("payload"), ExVarPattern.var("payload")))),
                ExExprBlock.block(
                    ExMatch.match(
                        ExVarPattern.var("event_type"),
                        ExCall.call(
                            "AwsEventStream",
                            "header_value",
                            ExVar.var("headers"),
                            ExString.string(":event-type"))),
                    ExCallLocal.callLocal(
                        "decode_" + helper + "_event_type",
                        ExVar.var("event_type"),
                        ExVar.var("payload"))))));
  }

  static ExFunction unionDecodeEventType(
      Model model, UnionShape union, SymbolProvider sp, String typesMod) {
    String helper = helperName(sp, union);
    List<ExClause> clauses = new ArrayList<>();
    for (MemberShape member : union.members()) {
      clauses.add(decodeEventTypeClause(model, helper, member, sp, typesMod));
    }
    clauses.add(
        ExClause.blockClause(
            List.of(ExVarPattern.var("event_type"), ExVarPattern.var("_payload")),
            ExCapturedBlock.capturedBlock(
                "raise ArgumentError, \"unknown event type: \" <> inspect(event_type)")));
    return ExFunction.defpFunction("decode_" + helper + "_event_type", clauses);
  }

  static String helperName(SymbolProvider sp, UnionShape union) {
    return sp.toSymbol(union).getName().replace("()", "");
  }

  private static ExClause encodeEventClause(
      Model model, String helper, MemberShape member, SymbolProvider sp, String typesMod) {
    String tag = ElixirUnionHelperIr.unionTagForMember(sp, member);
    String eventType = member.getMemberName();
    Shape target = model.expectShape(member.getTarget());
    return ExClause.blockClause(
        List.of(ExTuplePattern.tuple(ExAtomPattern.atom(tag), ExVarPattern.var("value"))),
        ExExprBlock.block(
            ExMatch.match(
                ExVarPattern.var("payload"),
                encodeMemberPayload(model, target, "value", sp, typesMod)),
            ExMatch.match(
                ExVarPattern.var("headers"),
                ExCall.call("AwsEventStream", "encode_event_headers", ExString.string(eventType))),
            ExCall.call("AwsEventStream", "frame", ExVar.var("headers"), ExVar.var("payload"))));
  }

  private static ExClause decodeEventTypeClause(
      Model model, String helper, MemberShape member, SymbolProvider sp, String typesMod) {
    String tag = ElixirUnionHelperIr.unionTagForMember(sp, member);
    String eventType = member.getMemberName();
    Shape target = model.expectShape(member.getTarget());
    return ExClause.blockClause(
        List.of(ExStringPattern.string(eventType), ExVarPattern.var("payload")),
        ExTuple.tuple(
            ExAtom.atom(tag), decodeMemberPayload(model, target, "payload", sp, typesMod)));
  }

  private static ExExpr encodeMemberPayload(
      Model model, Shape target, String valueVar, SymbolProvider sp, String typesMod) {
    if (target instanceof StructureShape structure) {
      return encodeStructurePayload(structure, valueVar, sp);
    }
    if (target instanceof BlobShape || target instanceof StringShape) {
      return ExVar.var(valueVar);
    }
    return ExCall.call("Jason", "encode!", ExVar.var(valueVar));
  }

  private static ExExpr decodeMemberPayload(
      Model model, Shape target, String payloadVar, SymbolProvider sp, String typesMod) {
    if (target instanceof StructureShape structure) {
      return decodeStructurePayload(structure, payloadVar, sp, typesMod);
    }
    if (target instanceof BlobShape || target instanceof StringShape) {
      return ExVar.var(payloadVar);
    }
    return ExCall.call("Jason", "decode!", ExVar.var(payloadVar));
  }

  private static ExExpr encodeStructurePayload(
      StructureShape structure, String valueVar, SymbolProvider sp) {
    if (structure.members().isEmpty()) {
      return ExCall.call("Jason", "encode!", ExMap.map());
    }
    List<ExMapEntry> entries = new ArrayList<>();
    for (MemberShape member : structure.members()) {
      String wireKey = jsonKey(member);
      String fieldName = BeamNameUtils.toSnakeCase(member.getMemberName());
      entries.add(
          ExMapEntry.entry(
              ExString.string(wireKey),
              ExCall.call("Map", "get", ExVar.var(valueVar), ExAtom.atom(fieldName))));
    }
    return ExCall.call("Jason", "encode!", ExMap.map(entries.toArray(ExMapEntry[]::new)));
  }

  private static ExExpr decodeStructurePayload(
      StructureShape structure, String payloadVar, SymbolProvider sp, String typesMod) {
    String structName = structName(sp.toSymbol(structure));
    if (structure.members().isEmpty()) {
      return ExStruct.struct(typesMod + "." + structName);
    }
    StringBuilder body = new StringBuilder();
    body.append("case Jason.decode!(").append(payloadVar).append(") do\n");
    body.append("  decoded ->\n");
    body.append("    %").append(typesMod).append(".").append(structName).append("{\n");
    List<MemberShape> members = new ArrayList<>(structure.members());
    for (int i = 0; i < members.size(); i++) {
      MemberShape member = members.get(i);
      String wireKey = jsonKey(member);
      String fieldName = BeamNameUtils.toSnakeCase(member.getMemberName());
      String comma = i < members.size() - 1 ? "," : "";
      body.append("      ")
          .append(fieldName)
          .append(": Map.get(decoded, \"")
          .append(wireKey)
          .append("\")")
          .append(comma)
          .append("\n");
    }
    body.append("    }\nend");
    return ExCapturedBlock.capturedBlock(body.toString());
  }

  private static String structName(Symbol symbol) {
    return symbol.getName().replace("()", "");
  }

  private static String jsonKey(MemberShape member) {
    return member
        .getTrait(JsonNameTrait.class)
        .map(JsonNameTrait::getValue)
        .orElse(member.getMemberName());
  }
}
