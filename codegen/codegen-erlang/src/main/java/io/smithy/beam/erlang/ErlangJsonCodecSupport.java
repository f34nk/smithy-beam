package io.smithy.beam.erlang;

import io.beam.dsl.erlang.AtomExpr;
import io.beam.dsl.erlang.AtomPattern;
import io.beam.dsl.erlang.BinaryExpr;
import io.beam.dsl.erlang.BinaryPattern;
import io.beam.dsl.erlang.CaseExpr;
import io.beam.dsl.erlang.Clause;
import io.beam.dsl.erlang.Expression;
import io.beam.dsl.erlang.LocalCallExpr;
import io.beam.dsl.erlang.MapEntry;
import io.beam.dsl.erlang.MapExpr;
import io.beam.dsl.erlang.MatchExpr;
import io.beam.dsl.erlang.RecordField;
import io.beam.dsl.erlang.RemoteCallExpr;
import io.beam.dsl.erlang.TuplePattern;
import io.beam.dsl.erlang.Variable;
import io.beam.dsl.erlang.VariablePattern;
import io.beam.dsl.erlang.WildcardPattern;
import io.smithy.beam.core.BeamEventStreamIndex;
import io.smithy.beam.core.BeamMemberNames;
import io.smithy.beam.core.BeamNameUtils;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import software.amazon.smithy.codegen.core.Symbol;
import software.amazon.smithy.codegen.core.SymbolProvider;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.knowledge.HttpBinding;
import software.amazon.smithy.model.knowledge.HttpBindingIndex;
import software.amazon.smithy.model.shapes.EnumShape;
import software.amazon.smithy.model.shapes.IntEnumShape;
import software.amazon.smithy.model.shapes.ListShape;
import software.amazon.smithy.model.shapes.MapShape;
import software.amazon.smithy.model.shapes.MemberShape;
import software.amazon.smithy.model.shapes.OperationShape;
import software.amazon.smithy.model.shapes.Shape;
import software.amazon.smithy.model.shapes.StructureShape;
import software.amazon.smithy.model.shapes.TimestampShape;
import software.amazon.smithy.model.shapes.UnionShape;
import software.amazon.smithy.model.traits.JsonNameTrait;
import software.amazon.smithy.model.traits.SparseTrait;

/** Shared JSON document encode/decode helpers for REST JSON and AWS JSON RPC emitters. */
final class ErlangJsonCodecSupport {

  private ErlangJsonCodecSupport() {}

  static String recordName(Symbol symbol) {
    return symbol.getName().replace("()", "");
  }

  static String toBindingVar(String snakeField) {
    return BeamNameUtils.toCamelCaseVariable(snakeField);
  }

  static String jsonKey(MemberShape member) {
    return member
        .getTrait(JsonNameTrait.class)
        .map(JsonNameTrait::getValue)
        .orElse(member.getMemberName());
  }

  static String documentDecodeAssignment(
      Model model,
      SymbolProvider sp,
      HttpBindingIndex httpIndex,
      String fieldName,
      MemberShape member) {
    String wireKey = jsonKey(member);
    String raw = "maps:get(<<\"" + wireKey + "\">>, Decoded, undefined)";
    return fieldName + " = " + decodeJsonValue(model, sp, httpIndex, member, raw);
  }

  static String encodeDocumentValue(
      Model model,
      SymbolProvider sp,
      HttpBindingIndex httpIndex,
      MemberShape member,
      String fieldName) {
    return encodeJsonValue(model, sp, httpIndex, member, toBindingVar(fieldName));
  }

  static String structureHelperName(SymbolProvider sp, Shape shape) {
    return sp.toSymbol(shape).getName().replace("()", "");
  }

  static String decodeJsonValue(
      Model model, SymbolProvider sp, HttpBindingIndex httpIndex, MemberShape member, String raw) {
    Shape target = model.expectShape(member.getTarget());
    if (target instanceof EnumShape || target instanceof IntEnumShape) {
      String helperName = structureHelperName(sp, target);
      return "decode_" + helperName + "(" + raw + ")";
    }
    if (target instanceof UnionShape union
        && !BeamEventStreamIndex.of(model).isEventStreamUnion(union)) {
      String helperName = structureHelperName(sp, target);
      return "decode_" + helperName + "(" + raw + ")";
    }
    if (target instanceof StructureShape) {
      String helperName = structureHelperName(sp, target);
      return "decode_" + helperName + "(" + raw + ")";
    }
    if (target instanceof TimestampShape) {
      String decodeHelper = timestampDecodeHelper(httpIndex, member, HttpBinding.Location.DOCUMENT);
      return decodeHelper + "(" + raw + ")";
    }
    if (target instanceof ListShape listShape) {
      Shape element = model.expectShape(listShape.getMember().getTarget());
      if (element instanceof StructureShape) {
        String helperName = structureHelperName(sp, element);
        return "decode_" + helperName + "_list(" + raw + ")";
      }
      if (element instanceof EnumShape || element instanceof IntEnumShape) {
        String helperName = structureHelperName(sp, element);
        return "decode_" + helperName + "_list(" + raw + ")";
      }
      String helper = target.hasTrait(SparseTrait.class) ? "decode_sparse_list" : "decode_list";
      return helper + "(" + raw + ")";
    }
    if (target instanceof MapShape mapShape) {
      return ErlangMapHelperDsl.mapDecodeExpr(model, sp, httpIndex, mapShape, raw);
    }
    return raw;
  }

  static String encodeJsonValue(
      Model model,
      SymbolProvider sp,
      HttpBindingIndex httpIndex,
      MemberShape member,
      String binding) {
    Shape target = model.expectShape(member.getTarget());
    if (target instanceof EnumShape || target instanceof IntEnumShape) {
      String helperName = structureHelperName(sp, target);
      return "encode_" + helperName + "(" + binding + ")";
    }
    if (target instanceof UnionShape union
        && !BeamEventStreamIndex.of(model).isEventStreamUnion(union)) {
      String helperName = structureHelperName(sp, target);
      return "encode_" + helperName + "(" + binding + ")";
    }
    if (target instanceof StructureShape) {
      String helperName = structureHelperName(sp, target);
      return "encode_" + helperName + "(" + binding + ")";
    }
    if (target instanceof TimestampShape) {
      String encodeHelper = timestampEncodeHelper(httpIndex, member, HttpBinding.Location.DOCUMENT);
      return encodeHelper + "(" + binding + ")";
    }
    if (target instanceof ListShape listShape) {
      Shape element = model.expectShape(listShape.getMember().getTarget());
      if (element instanceof StructureShape) {
        String helperName = structureHelperName(sp, element);
        return "encode_" + helperName + "_list(" + binding + ")";
      }
      if (element instanceof EnumShape || element instanceof IntEnumShape) {
        String helperName = structureHelperName(sp, element);
        return "encode_" + helperName + "_list(" + binding + ")";
      }
      if (target.hasTrait(SparseTrait.class)) {
        return "encode_sparse_list(" + binding + ")";
      }
      return binding;
    }
    if (target instanceof MapShape mapShape) {
      return ErlangMapHelperDsl.mapEncodeExpr(model, sp, httpIndex, mapShape, binding);
    }
    return binding;
  }

  static String encodeJsonValueFromRecord(
      Model model,
      SymbolProvider sp,
      HttpBindingIndex httpIndex,
      StructureShape parent,
      MemberShape member,
      String recordVar) {
    String fieldName = BeamMemberNames.fieldName(sp, member);
    String recordName = structureHelperName(sp, parent);
    return encodeJsonValue(
        model, sp, httpIndex, member, recordVar + "#" + recordName + "." + fieldName);
  }

  static String timestampEncodeHelper(
      HttpBindingIndex httpIndex, MemberShape member, HttpBinding.Location location) {
    var fmt =
        httpIndex.determineTimestampFormat(
            member,
            location,
            software.amazon.smithy.model.traits.TimestampFormatTrait.Format.DATE_TIME);
    return fmt == software.amazon.smithy.model.traits.TimestampFormatTrait.Format.EPOCH_SECONDS
        ? "encode_timestamp_epoch_seconds"
        : "encode_timestamp_date_time";
  }

  static String timestampDecodeHelper(
      HttpBindingIndex httpIndex, MemberShape member, HttpBinding.Location location) {
    var fmt =
        httpIndex.determineTimestampFormat(
            member,
            location,
            software.amazon.smithy.model.traits.TimestampFormatTrait.Format.DATE_TIME);
    return fmt == software.amazon.smithy.model.traits.TimestampFormatTrait.Format.EPOCH_SECONDS
        ? "decode_timestamp_epoch_seconds"
        : "decode_timestamp_date_time";
  }

  static List<String> inputPatternParts(SymbolProvider sp, StructureShape input) {
    List<String> parts = new ArrayList<>();
    for (MemberShape member : input.members()) {
      String field = BeamMemberNames.fieldName(sp, member);
      parts.add(field + " = " + toBindingVar(field));
    }
    return parts;
  }

  static String inputPattern(SymbolProvider sp, StructureShape input) {
    List<String> parts = inputPatternParts(sp, input);
    return parts.isEmpty() ? "" : "\n    " + String.join(",\n    ", parts) + "\n";
  }

  static List<MemberShape> documentMembers(
      HttpBindingIndex httpIndex, OperationShape op, StructureShape structure, boolean request) {
    List<HttpBinding> bindings =
        request
            ? httpIndex.getRequestBindings(op, HttpBinding.Location.DOCUMENT)
            : httpIndex.getResponseBindings(op, HttpBinding.Location.DOCUMENT);
    if (bindings.isEmpty() && request) {
      bindings = httpIndex.getRequestBindings(op, HttpBinding.Location.PAYLOAD);
    } else if (bindings.isEmpty()) {
      bindings = httpIndex.getResponseBindings(op, HttpBinding.Location.PAYLOAD);
    }
    if (!bindings.isEmpty()) {
      return bindings.stream().map(HttpBinding::getMember).collect(Collectors.toList());
    }
    return new ArrayList<>(structure.members());
  }

  static boolean isEventStreamPayload(List<MemberShape> members, Model model) {
    return members.size() == 1
        && BeamEventStreamIndex.of(model).isEventStreamMember(members.get(0));
  }

  static List<MapEntry> bodyMapEntries(
      Model model,
      HttpBindingIndex httpIndex,
      SymbolProvider sp,
      List<MemberShape> members,
      HttpBinding.Location location,
      String eventStreamModule) {
    List<MapEntry> entries = new ArrayList<>();
    for (MemberShape member : members) {
      String fieldName = BeamMemberNames.fieldName(sp, member);
      Shape target = model.expectShape(member.getTarget());
      if (target instanceof UnionShape union
          && BeamEventStreamIndex.of(model).isEventStreamUnion(union)) {
        String helper = ErlangEventStreamEmitter.helperName(sp, union);
        entries.add(
            MapEntry.of(
                BinaryExpr.of(jsonKey(member)),
                RemoteCallExpr.of(
                    eventStreamModule,
                    "encode_" + helper,
                    List.of(Variable.of(toBindingVar(fieldName))))));
      } else {
        entries.add(
            MapEntry.of(
                BinaryExpr.of(jsonKey(member)),
                encodeJsonExpr(model, sp, httpIndex, member, toBindingVar(fieldName))));
      }
    }
    return entries;
  }

  static List<RecordField> recordFieldsFromDecoded(
      Model model,
      HttpBindingIndex httpIndex,
      SymbolProvider sp,
      List<MemberShape> members,
      HttpBinding.Location location,
      String eventStreamModule) {
    List<RecordField> fields = new ArrayList<>();
    for (MemberShape member : members) {
      String fieldName = BeamMemberNames.fieldName(sp, member);
      Shape target = model.expectShape(member.getTarget());
      if (target instanceof UnionShape union
          && BeamEventStreamIndex.of(model).isEventStreamUnion(union)) {
        String helper = ErlangEventStreamEmitter.helperName(sp, union);
        fields.add(
            RecordField.of(
                fieldName,
                RemoteCallExpr.of(
                    eventStreamModule, "decode_" + helper, List.of(Variable.of("Body")))));
      } else {
        Expression raw =
            ErlangCodecHelperDsl.mapsGetDefault(
                BinaryExpr.of(jsonKey(member)), Variable.of("Decoded"), AtomExpr.of("undefined"));
        fields.add(RecordField.of(fieldName, decodeJsonExpr(model, sp, httpIndex, member, raw)));
      }
    }
    return fields;
  }

  static List<Expression> decodedBodyPrelude() {
    return List.of(MatchExpr.bindValue("Decoded", decodedBodyExpr()));
  }

  static CaseExpr decodedBodyExpr() {
    return CaseExpr.of(
        Variable.of("Body"),
        List.of(
            Clause.of(BinaryPattern.of(""), MapExpr.of(List.of())),
            Clause.of(
                WildcardPattern.of(),
                CaseExpr.of(
                    RemoteCallExpr.of("jsone", "try_decode", List.of(Variable.of("Body"))),
                    List.of(
                        Clause.of(
                            TuplePattern.of(
                                List.of(
                                    AtomPattern.of("ok"),
                                    VariablePattern.of("Val"),
                                    WildcardPattern.of())),
                            Variable.of("Val")),
                        Clause.of(
                            TuplePattern.of(List.of(AtomPattern.of("error"), WildcardPattern.of())),
                            MapExpr.of(List.of())))))));
  }

  static Expression decodeJsonExpr(
      Model model,
      SymbolProvider sp,
      HttpBindingIndex httpIndex,
      MemberShape member,
      Expression raw) {
    Shape target = model.expectShape(member.getTarget());
    if (target instanceof EnumShape || target instanceof IntEnumShape) {
      String helperName = structureHelperName(sp, target);
      return LocalCallExpr.of("decode_" + helperName, List.of(raw));
    }
    if (target instanceof UnionShape union
        && !BeamEventStreamIndex.of(model).isEventStreamUnion(union)) {
      String helperName = structureHelperName(sp, target);
      return LocalCallExpr.of("decode_" + helperName, List.of(raw));
    }
    if (target instanceof StructureShape) {
      String helperName = structureHelperName(sp, target);
      return LocalCallExpr.of("decode_" + helperName, List.of(raw));
    }
    if (target instanceof TimestampShape) {
      String decodeHelper = timestampDecodeHelper(httpIndex, member, HttpBinding.Location.DOCUMENT);
      return LocalCallExpr.of(decodeHelper, List.of(raw));
    }
    if (target instanceof ListShape listShape) {
      Shape element = model.expectShape(listShape.getMember().getTarget());
      if (element instanceof StructureShape) {
        String helperName = structureHelperName(sp, element);
        return LocalCallExpr.of("decode_" + helperName + "_list", List.of(raw));
      }
      if (element instanceof EnumShape || element instanceof IntEnumShape) {
        String helperName = structureHelperName(sp, element);
        return LocalCallExpr.of("decode_" + helperName + "_list", List.of(raw));
      }
      String helper = target.hasTrait(SparseTrait.class) ? "decode_sparse_list" : "decode_list";
      return LocalCallExpr.of(helper, List.of(raw));
    }
    if (target instanceof MapShape mapShape) {
      return ErlangMapHelperDsl.mapDecodeExpr(model, sp, httpIndex, mapShape, raw);
    }
    return raw;
  }

  static Expression encodeJsonExpr(
      Model model,
      SymbolProvider sp,
      HttpBindingIndex httpIndex,
      MemberShape member,
      String bindingVar) {
    Shape target = model.expectShape(member.getTarget());
    if (target instanceof EnumShape || target instanceof IntEnumShape) {
      String helperName = structureHelperName(sp, target);
      return LocalCallExpr.of("encode_" + helperName, List.of(Variable.of(bindingVar)));
    }
    if (target instanceof UnionShape union
        && !BeamEventStreamIndex.of(model).isEventStreamUnion(union)) {
      String helperName = structureHelperName(sp, target);
      return LocalCallExpr.of("encode_" + helperName, List.of(Variable.of(bindingVar)));
    }
    if (target instanceof StructureShape) {
      String helperName = structureHelperName(sp, target);
      return LocalCallExpr.of("encode_" + helperName, List.of(Variable.of(bindingVar)));
    }
    if (target instanceof TimestampShape) {
      String encodeHelper = timestampEncodeHelper(httpIndex, member, HttpBinding.Location.DOCUMENT);
      return LocalCallExpr.of(encodeHelper, List.of(Variable.of(bindingVar)));
    }
    if (target instanceof ListShape listShape) {
      Shape element = model.expectShape(listShape.getMember().getTarget());
      if (element instanceof StructureShape) {
        String helperName = structureHelperName(sp, element);
        return LocalCallExpr.of("encode_" + helperName + "_list", List.of(Variable.of(bindingVar)));
      }
      if (element instanceof EnumShape || element instanceof IntEnumShape) {
        String helperName = structureHelperName(sp, element);
        return LocalCallExpr.of("encode_" + helperName + "_list", List.of(Variable.of(bindingVar)));
      }
      if (target.hasTrait(SparseTrait.class)) {
        return LocalCallExpr.of("encode_sparse_list", List.of(Variable.of(bindingVar)));
      }
      return Variable.of(bindingVar);
    }
    if (target instanceof MapShape mapShape) {
      return ErlangMapHelperDsl.mapEncodeExpr(
          model, sp, httpIndex, mapShape, Variable.of(bindingVar));
    }
    return Variable.of(bindingVar);
  }
}
