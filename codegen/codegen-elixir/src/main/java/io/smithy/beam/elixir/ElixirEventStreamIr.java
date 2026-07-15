package io.smithy.beam.elixir;

import io.beam.dsl.elixir.Alias;
import io.beam.dsl.elixir.AtomExpr;
import io.beam.dsl.elixir.AtomPattern;
import io.beam.dsl.elixir.BlockExpr;
import io.beam.dsl.elixir.CaptureExpr;
import io.beam.dsl.elixir.CaseExpr;
import io.beam.dsl.elixir.Clause;
import io.beam.dsl.elixir.Expression;
import io.beam.dsl.elixir.Function;
import io.beam.dsl.elixir.FunctionDoc;
import io.beam.dsl.elixir.FunctionHead;
import io.beam.dsl.elixir.InfixExpr;
import io.beam.dsl.elixir.IsTypeGuard;
import io.beam.dsl.elixir.LocalCallExpr;
import io.beam.dsl.elixir.MapEntry;
import io.beam.dsl.elixir.MapExpr;
import io.beam.dsl.elixir.MapPattern;
import io.beam.dsl.elixir.MapPatternEntry;
import io.beam.dsl.elixir.MatchExpr;
import io.beam.dsl.elixir.Module;
import io.beam.dsl.elixir.Moduledoc;
import io.beam.dsl.elixir.Pattern;
import io.beam.dsl.elixir.PipeExpr;
import io.beam.dsl.elixir.PipeStep;
import io.beam.dsl.elixir.RaiseExpr;
import io.beam.dsl.elixir.RemoteCallExpr;
import io.beam.dsl.elixir.StringExpr;
import io.beam.dsl.elixir.StringPattern;
import io.beam.dsl.elixir.StructExpr;
import io.beam.dsl.elixir.StructField;
import io.beam.dsl.elixir.TupleExpr;
import io.beam.dsl.elixir.TuplePattern;
import io.beam.dsl.elixir.Variable;
import io.beam.dsl.elixir.VariablePattern;
import io.beam.dsl.elixir.WildcardPattern;
import io.smithy.beam.core.BeamElixirLayout;
import io.smithy.beam.core.BeamEventStreamIndex;
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

final class ElixirEventStreamIr {
  private ElixirEventStreamIr() {}

  static Module eventStreamModule(ElixirContext ctx, ServiceShape service) {
    BeamElixirLayout layout =
        new BeamElixirLayout(ctx.settings(), service.getId().getNamespace(), service);
    String moduleName = ElixirSymbolProvider.toModuleName(layout.eventStreamModuleName());
    String typesMod = ElixirSymbolProvider.toModuleName(layout.typesModuleName());
    BeamEventStreamIndex index = BeamEventStreamIndex.of(ctx.model());
    List<UnionShape> unions = index.eventStreamUnions(service);
    SymbolProvider sp = ctx.symbolProvider();
    Model model = ctx.model();

    List<Function> functions = new ArrayList<>();
    for (UnionShape union : unions) {
      functions.addAll(unionHelpers(model, union, sp, typesMod));
    }

    return new Module(
        moduleName,
        Moduledoc.of(
            "Generated Amazon Event Stream helpers for " + service.getId() + " (generated)."),
        List.of(),
        List.of(Alias.of(typesMod)),
        List.of(),
        List.of(),
        List.of(),
        List.of(),
        functions);
  }

  static List<Function> unionHelpers(
      Model model, UnionShape union, SymbolProvider sp, String typesMod) {
    List<Function> functions = new ArrayList<>();
    functions.add(unionEncodeList(union, sp));
    functions.add(unionDecodeList(union, sp));
    functions.addAll(unionEncodeEvent(model, union, sp, typesMod));
    functions.add(unionDecodeEvent(union, sp));
    functions.addAll(unionDecodeEventType(model, union, sp, typesMod));
    return functions;
  }

  static Function unionEncodeList(UnionShape union, SymbolProvider sp) {
    String helper = helperName(sp, union);
    return new Function(
        "encode_" + helper,
        false,
        List.of(
            FunctionHead.of(
                List.of(VariablePattern.of("events")), IsTypeGuard.of("is_list", "events"))),
        RemoteCallExpr.of(
            "Enum",
            "map",
            List.of(Variable.of("events"), CaptureExpr.of("encode_" + helper + "_event", 1))),
        null,
        FunctionDoc.of("Encodes a list of event stream events into framed binaries."),
        false);
  }

  static Function unionDecodeList(UnionShape union, SymbolProvider sp) {
    String helper = helperName(sp, union);
    return new Function(
        "decode_" + helper,
        false,
        List.of(
            FunctionHead.of(
                List.of(VariablePattern.of("body")), IsTypeGuard.of("is_binary", "body"))),
        new PipeExpr(
            Variable.of("body"),
            List.of(
                new PipeStep(
                    RemoteCallExpr.of("AwsEventStream", "decode_frames", List.of()), List.of()),
                new PipeStep(
                    RemoteCallExpr.of(
                        "Enum", "map", List.of(CaptureExpr.of("decode_" + helper + "_event", 1))),
                    List.of()))),
        null,
        FunctionDoc.of("Decodes an event stream body into tagged events."),
        false);
  }

  static List<Function> unionEncodeEvent(
      Model model, UnionShape union, SymbolProvider sp, String typesMod) {
    String helper = helperName(sp, union);
    List<Function> functions = new ArrayList<>();
    for (MemberShape member : union.members()) {
      functions.add(encodeEventClause(model, helper, member, sp, typesMod));
    }
    functions.add(
        defp(
            "encode_" + helper + "_event",
            List.of(TuplePattern.of(List.of(AtomPattern.of("unknown"), WildcardPattern.of()))),
            new RaiseExpr(AtomExpr.of("ArgumentError"), StringExpr.of("unknown event"), true),
            true));
    return functions;
  }

  static Function unionDecodeEvent(UnionShape union, SymbolProvider sp) {
    String helper = helperName(sp, union);
    return defp(
        "decode_" + helper + "_event",
        List.of(
            MapPattern.of(
                List.of(
                    MapPatternEntry.of(AtomExpr.of("headers"), VariablePattern.of("headers")),
                    MapPatternEntry.of(AtomExpr.of("payload"), VariablePattern.of("payload"))))),
        new BlockExpr(
            List.of(
                MatchExpr.bind(
                    "event_type",
                    RemoteCallExpr.of(
                        "AwsEventStream",
                        "header_value",
                        List.of(Variable.of("headers"), StringExpr.of(":event-type"))),
                    LocalCallExpr.of(
                        "decode_" + helper + "_event_type",
                        List.of(Variable.of("event_type"), Variable.of("payload")))))),
        false);
  }

  static List<Function> unionDecodeEventType(
      Model model, UnionShape union, SymbolProvider sp, String typesMod) {
    String helper = helperName(sp, union);
    List<Function> functions = new ArrayList<>();
    for (MemberShape member : union.members()) {
      functions.add(decodeEventTypeClause(model, helper, member, sp, typesMod));
    }
    functions.add(
        defp(
            "decode_" + helper + "_event_type",
            List.of(VariablePattern.of("event_type"), VariablePattern.of("_payload")),
            new RaiseExpr(
                AtomExpr.of("ArgumentError"),
                new InfixExpr(
                    StringExpr.of("unknown event type: "),
                    "<>",
                    RemoteCallExpr.of("Kernel", "inspect", List.of(Variable.of("event_type")))),
                false),
            false));
    return functions;
  }

  static String helperName(SymbolProvider sp, UnionShape union) {
    return sp.toSymbol(union).getName().replace("()", "");
  }

  private static Function encodeEventClause(
      Model model, String helper, MemberShape member, SymbolProvider sp, String typesMod) {
    String tag = ElixirUnionHelperIr.unionTagForMember(sp, member);
    String eventType = member.getMemberName();
    Shape target = model.expectShape(member.getTarget());
    return defp(
        "encode_" + helper + "_event",
        List.of(TuplePattern.of(List.of(AtomPattern.of(tag), VariablePattern.of("value")))),
        MatchExpr.bind(
            "payload",
            encodeMemberPayload(model, target, "value", sp, typesMod),
            MatchExpr.bind(
                "headers",
                RemoteCallExpr.of(
                    "AwsEventStream", "encode_event_headers", List.of(StringExpr.of(eventType))),
                RemoteCallExpr.of(
                    "AwsEventStream",
                    "frame",
                    List.of(Variable.of("headers"), Variable.of("payload"))))),
        false);
  }

  private static Function decodeEventTypeClause(
      Model model, String helper, MemberShape member, SymbolProvider sp, String typesMod) {
    String tag = ElixirUnionHelperIr.unionTagForMember(sp, member);
    String eventType = member.getMemberName();
    Shape target = model.expectShape(member.getTarget());
    return defp(
        "decode_" + helper + "_event_type",
        List.of(StringPattern.of(eventType), VariablePattern.of("payload")),
        TupleExpr.of(
            List.of(AtomExpr.of(tag), decodeMemberPayload(model, target, "payload", sp, typesMod))),
        false);
  }

  private static Expression encodeMemberPayload(
      Model model, Shape target, String valueVar, SymbolProvider sp, String typesMod) {
    if (target instanceof StructureShape structure) {
      return encodeStructurePayload(structure, valueVar, sp);
    }
    if (target instanceof BlobShape || target instanceof StringShape) {
      return Variable.of(valueVar);
    }
    return RemoteCallExpr.of("Jason", "encode!", List.of(Variable.of(valueVar)));
  }

  private static Expression decodeMemberPayload(
      Model model, Shape target, String payloadVar, SymbolProvider sp, String typesMod) {
    if (target instanceof StructureShape structure) {
      return decodeStructurePayload(structure, payloadVar, sp, typesMod);
    }
    if (target instanceof BlobShape || target instanceof StringShape) {
      return Variable.of(payloadVar);
    }
    return RemoteCallExpr.of("Jason", "decode!", List.of(Variable.of(payloadVar)));
  }

  private static Expression encodeStructurePayload(
      StructureShape structure, String valueVar, SymbolProvider sp) {
    if (structure.members().isEmpty()) {
      return RemoteCallExpr.of("Jason", "encode!", List.of(MapExpr.of(List.of())));
    }
    List<MapEntry> entries = new ArrayList<>();
    for (MemberShape member : structure.members()) {
      String wireKey = jsonKey(member);
      String fieldName = BeamNameUtils.toSnakeCase(member.getMemberName());
      entries.add(
          MapEntry.stringKey(
              wireKey,
              RemoteCallExpr.of(
                  "Map", "get", List.of(Variable.of(valueVar), AtomExpr.of(fieldName)))));
    }
    return RemoteCallExpr.of("Jason", "encode!", List.of(MapExpr.of(entries)));
  }

  private static Expression decodeStructurePayload(
      StructureShape structure, String payloadVar, SymbolProvider sp, String typesMod) {
    String structName = structName(sp.toSymbol(structure));
    if (structure.members().isEmpty()) {
      return StructExpr.of(typesMod + "." + structName, List.of());
    }
    List<StructField> fields = new ArrayList<>();
    for (MemberShape member : structure.members()) {
      String wireKey = jsonKey(member);
      String fieldName = BeamNameUtils.toSnakeCase(member.getMemberName());
      fields.add(
          StructField.of(
              fieldName,
              RemoteCallExpr.of(
                  "Map", "get", List.of(Variable.of("decoded"), StringExpr.of(wireKey)))));
    }
    return new CaseExpr(
        RemoteCallExpr.of("Jason", "decode!", List.of(Variable.of(payloadVar))),
        List.of(
            Clause.of(
                VariablePattern.of("decoded"),
                StructExpr.of(typesMod + "." + structName, fields))));
  }

  private static Function defp(
      String name, List<Pattern> params, Expression body, boolean oneLiner) {
    return new Function(name, true, List.of(FunctionHead.of(params)), body, null, null, oneLiner);
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
