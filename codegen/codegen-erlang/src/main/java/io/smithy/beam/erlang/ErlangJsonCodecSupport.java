package io.smithy.beam.erlang;

import io.smithy.beam.core.BeamEventStreamIndex;
import io.smithy.beam.core.BeamNameUtils;
import io.smithy.beam.ir.erlang.ErlAtom;
import io.smithy.beam.ir.erlang.ErlAtomPattern;
import io.smithy.beam.ir.erlang.ErlBinary;
import io.smithy.beam.ir.erlang.ErlBinaryPattern;
import io.smithy.beam.ir.erlang.ErlCall;
import io.smithy.beam.ir.erlang.ErlCallLocal;
import io.smithy.beam.ir.erlang.ErlCase;
import io.smithy.beam.ir.erlang.ErlClause;
import io.smithy.beam.ir.erlang.ErlExpr;
import io.smithy.beam.ir.erlang.ErlMap;
import io.smithy.beam.ir.erlang.ErlMapEntry;
import io.smithy.beam.ir.erlang.ErlMatch;
import io.smithy.beam.ir.erlang.ErlRecordField;
import io.smithy.beam.ir.erlang.ErlTuplePattern;
import io.smithy.beam.ir.erlang.ErlVar;
import io.smithy.beam.ir.erlang.ErlVarPattern;
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
      String helper = target.hasTrait(SparseTrait.class) ? "decode_sparse_list" : "decode_list";
      return helper + "(" + raw + ")";
    }
    if (target instanceof MapShape) {
      if (target.hasTrait(SparseTrait.class)) {
        return "decode_sparse_map(" + raw + ")";
      }
      return raw;
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
      if (target.hasTrait(SparseTrait.class)) {
        return "encode_sparse_list(" + binding + ")";
      }
      return binding;
    }
    if (target instanceof MapShape) {
      if (target.hasTrait(SparseTrait.class)) {
        return "encode_sparse_map(" + binding + ")";
      }
      return binding;
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
    String fieldName = BeamNameUtils.toSnakeCase(member.getMemberName());
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

  static List<String> inputPatternParts(StructureShape input) {
    List<String> parts = new ArrayList<>();
    for (MemberShape member : input.members()) {
      String field = BeamNameUtils.toSnakeCase(member.getMemberName());
      parts.add(field + " = " + toBindingVar(field));
    }
    return parts;
  }

  static String inputPattern(StructureShape input) {
    List<String> parts = inputPatternParts(input);
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

  static List<ErlMapEntry> bodyMapEntries(
      Model model,
      HttpBindingIndex httpIndex,
      SymbolProvider sp,
      List<MemberShape> members,
      HttpBinding.Location location,
      String eventStreamModule) {
    List<ErlMapEntry> entries = new ArrayList<>();
    for (MemberShape member : members) {
      String fieldName = BeamNameUtils.toSnakeCase(member.getMemberName());
      Shape target = model.expectShape(member.getTarget());
      if (target instanceof UnionShape union
          && BeamEventStreamIndex.of(model).isEventStreamUnion(union)) {
        String helper = ErlangEventStreamEmitter.helperName(sp, union);
        entries.add(
            ErlMapEntry.entry(
                ErlBinary.binary(jsonKey(member)),
                ErlCall.call(
                    eventStreamModule, "encode_" + helper, ErlVar.var(toBindingVar(fieldName)))));
      } else {
        entries.add(
            ErlMapEntry.entry(
                ErlBinary.binary(jsonKey(member)),
                encodeJsonExpr(model, sp, httpIndex, member, toBindingVar(fieldName))));
      }
    }
    return entries;
  }

  static List<ErlRecordField> recordFieldsFromDecoded(
      Model model,
      HttpBindingIndex httpIndex,
      SymbolProvider sp,
      List<MemberShape> members,
      HttpBinding.Location location,
      String eventStreamModule) {
    List<ErlRecordField> fields = new ArrayList<>();
    for (MemberShape member : members) {
      String fieldName = BeamNameUtils.toSnakeCase(member.getMemberName());
      Shape target = model.expectShape(member.getTarget());
      if (target instanceof UnionShape union
          && BeamEventStreamIndex.of(model).isEventStreamUnion(union)) {
        String helper = ErlangEventStreamEmitter.helperName(sp, union);
        fields.add(
            ErlRecordField.field(
                fieldName,
                ErlCall.call(eventStreamModule, "decode_" + helper, ErlVar.var("Body"))));
      } else {
        ErlExpr raw =
            ErlangCodecHelperIr.mapsGetDefault(
                ErlBinary.binary(jsonKey(member)),
                ErlVar.var("Decoded"),
                ErlAtom.atom("undefined"));
        fields.add(
            ErlRecordField.field(fieldName, decodeJsonExpr(model, sp, httpIndex, member, raw)));
      }
    }
    return fields;
  }

  static List<ErlExpr> decodedBodyPrelude() {
    return List.of(ErlMatch.match(ErlVarPattern.varPattern("Decoded"), decodedBodyExpr()));
  }

  static ErlCase decodedBodyExpr() {
    return ErlCase.caseExpr(
        ErlVar.var("Body"),
        ErlClause.clause(List.of(ErlBinaryPattern.binaryPattern("")), ErlMap.map()),
        ErlClause.clause(
            List.of(ErlVarPattern.varPattern("_")),
            ErlCase.caseExpr(
                ErlCall.call("jsone", "try_decode", ErlVar.var("Body")),
                ErlClause.clause(
                    List.of(
                        ErlTuplePattern.tuplePattern(
                            ErlAtomPattern.atomPattern("ok"),
                            ErlVarPattern.varPattern("Val"),
                            ErlVarPattern.varPattern("_"))),
                    ErlVar.var("Val")),
                ErlClause.clause(
                    List.of(
                        ErlTuplePattern.tuplePattern(
                            ErlAtomPattern.atomPattern("error"), ErlVarPattern.varPattern("_"))),
                    ErlMap.map()))));
  }

  static ErlExpr decodeJsonExpr(
      Model model, SymbolProvider sp, HttpBindingIndex httpIndex, MemberShape member, ErlExpr raw) {
    Shape target = model.expectShape(member.getTarget());
    if (target instanceof EnumShape || target instanceof IntEnumShape) {
      String helperName = structureHelperName(sp, target);
      return ErlCallLocal.callLocal("decode_" + helperName, raw);
    }
    if (target instanceof UnionShape union
        && !BeamEventStreamIndex.of(model).isEventStreamUnion(union)) {
      String helperName = structureHelperName(sp, target);
      return ErlCallLocal.callLocal("decode_" + helperName, raw);
    }
    if (target instanceof StructureShape) {
      String helperName = structureHelperName(sp, target);
      return ErlCallLocal.callLocal("decode_" + helperName, raw);
    }
    if (target instanceof TimestampShape) {
      String decodeHelper = timestampDecodeHelper(httpIndex, member, HttpBinding.Location.DOCUMENT);
      return ErlCallLocal.callLocal(decodeHelper, raw);
    }
    if (target instanceof ListShape listShape) {
      Shape element = model.expectShape(listShape.getMember().getTarget());
      if (element instanceof StructureShape) {
        String helperName = structureHelperName(sp, element);
        return ErlCallLocal.callLocal("decode_" + helperName + "_list", raw);
      }
      String helper = target.hasTrait(SparseTrait.class) ? "decode_sparse_list" : "decode_list";
      return ErlCallLocal.callLocal(helper, raw);
    }
    if (target instanceof MapShape) {
      if (target.hasTrait(SparseTrait.class)) {
        return ErlCallLocal.callLocal("decode_sparse_map", raw);
      }
      return raw;
    }
    return raw;
  }

  static ErlExpr encodeJsonExpr(
      Model model,
      SymbolProvider sp,
      HttpBindingIndex httpIndex,
      MemberShape member,
      String bindingVar) {
    Shape target = model.expectShape(member.getTarget());
    if (target instanceof EnumShape || target instanceof IntEnumShape) {
      String helperName = structureHelperName(sp, target);
      return ErlCallLocal.callLocal("encode_" + helperName, ErlVar.var(bindingVar));
    }
    if (target instanceof UnionShape union
        && !BeamEventStreamIndex.of(model).isEventStreamUnion(union)) {
      String helperName = structureHelperName(sp, target);
      return ErlCallLocal.callLocal("encode_" + helperName, ErlVar.var(bindingVar));
    }
    if (target instanceof StructureShape) {
      String helperName = structureHelperName(sp, target);
      return ErlCallLocal.callLocal("encode_" + helperName, ErlVar.var(bindingVar));
    }
    if (target instanceof TimestampShape) {
      String encodeHelper = timestampEncodeHelper(httpIndex, member, HttpBinding.Location.DOCUMENT);
      return ErlCallLocal.callLocal(encodeHelper, ErlVar.var(bindingVar));
    }
    if (target instanceof ListShape listShape) {
      Shape element = model.expectShape(listShape.getMember().getTarget());
      if (element instanceof StructureShape) {
        String helperName = structureHelperName(sp, element);
        return ErlCallLocal.callLocal("encode_" + helperName + "_list", ErlVar.var(bindingVar));
      }
      if (target.hasTrait(SparseTrait.class)) {
        return ErlCallLocal.callLocal("encode_sparse_list", ErlVar.var(bindingVar));
      }
      return ErlVar.var(bindingVar);
    }
    if (target instanceof MapShape) {
      if (target.hasTrait(SparseTrait.class)) {
        return ErlCallLocal.callLocal("encode_sparse_map", ErlVar.var(bindingVar));
      }
      return ErlVar.var(bindingVar);
    }
    return ErlVar.var(bindingVar);
  }
}
