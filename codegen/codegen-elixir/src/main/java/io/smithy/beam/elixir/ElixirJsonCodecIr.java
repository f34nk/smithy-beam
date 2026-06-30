package io.smithy.beam.elixir;

import io.smithy.beam.core.BeamEventStreamIndex;
import io.smithy.beam.core.BeamNameUtils;
import io.smithy.beam.ir.elixir.ExAnonymousFn;
import io.smithy.beam.ir.elixir.ExAtom;
import io.smithy.beam.ir.elixir.ExCall;
import io.smithy.beam.ir.elixir.ExCallLocal;
import io.smithy.beam.ir.elixir.ExClause;
import io.smithy.beam.ir.elixir.ExExpr;
import io.smithy.beam.ir.elixir.ExIf;
import io.smithy.beam.ir.elixir.ExMap;
import io.smithy.beam.ir.elixir.ExMapEntry;
import io.smithy.beam.ir.elixir.ExMatch;
import io.smithy.beam.ir.elixir.ExOp;
import io.smithy.beam.ir.elixir.ExPipeline;
import io.smithy.beam.ir.elixir.ExString;
import io.smithy.beam.ir.elixir.ExStructAccess;
import io.smithy.beam.ir.elixir.ExTuplePattern;
import io.smithy.beam.ir.elixir.ExVar;
import io.smithy.beam.ir.elixir.ExVarPattern;
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
import software.amazon.smithy.model.traits.TimestampFormatTrait;

final class ElixirJsonCodecIr {
  private ElixirJsonCodecIr() {}

  static String structName(Symbol symbol) {
    return symbol.getName();
  }

  static String jsonKey(MemberShape member) {
    return member
        .getTrait(JsonNameTrait.class)
        .map(JsonNameTrait::getValue)
        .orElse(member.getMemberName());
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

  static List<ExMapEntry> bodyMapEntries(
      Model model,
      HttpBindingIndex httpIndex,
      SymbolProvider sp,
      String typesMod,
      List<MemberShape> members,
      String recordVar,
      String eventStreamModule) {
    List<ExMapEntry> entries = new ArrayList<>();
    for (MemberShape member : members) {
      String fieldName = fieldName(sp, member);
      Shape target = model.expectShape(member.getTarget());
      if (target instanceof UnionShape union
          && BeamEventStreamIndex.of(model).isEventStreamUnion(union)) {
        String helper = ElixirEventStreamEmitter.helperName(sp, union);
        entries.add(
            ExMapEntry.entry(
                ExString.string(jsonKey(member)),
                ExCall.call(
                    eventStreamModule,
                    "encode_" + helper,
                    ExStructAccess.structAccess(ExVar.var(recordVar), fieldName))));
      } else {
        entries.add(
            ExMapEntry.entry(
                ExString.string(jsonKey(member)),
                encodeJsonExpr(
                    model,
                    sp,
                    httpIndex,
                    member,
                    ExStructAccess.structAccess(ExVar.var(recordVar), fieldName))));
      }
    }
    return entries;
  }

  static List<ExMapEntry> structFieldEntriesFromDecoded(
      Model model,
      HttpBindingIndex httpIndex,
      SymbolProvider sp,
      String typesMod,
      List<MemberShape> members,
      String eventStreamModule) {
    List<ExMapEntry> fields = new ArrayList<>();
    for (MemberShape member : members) {
      String fieldName = fieldName(sp, member);
      Shape target = model.expectShape(member.getTarget());
      if (target instanceof UnionShape union
          && BeamEventStreamIndex.of(model).isEventStreamUnion(union)) {
        String helper = ElixirEventStreamEmitter.helperName(sp, union);
        fields.add(
            ExMapEntry.entry(
                ExAtom.atom(fieldName),
                ExCall.call(eventStreamModule, "decode_" + helper, ExVar.var("body"))));
      } else {
        ExExpr raw =
            ExCall.call("Map", "get", ExVar.var("decoded"), ExString.string(jsonKey(member)));
        fields.add(
            ExMapEntry.entry(
                ExAtom.atom(fieldName), decodeJsonExpr(model, sp, httpIndex, member, raw)));
      }
    }
    return fields;
  }

  static List<ExExpr> decodedBodyPrelude() {
    return List.of(ExMatch.match(ExVarPattern.var("decoded"), decodedBodyExpr()));
  }

  static ExExpr decodedBodyExpr() {
    return ExIf.ifExpr(
        ExOp.op(
            "or",
            ExOp.op("==", ExVar.var("body"), ExString.string("")),
            ExCall.call("Kernel", "is_nil", ExVar.var("body"))),
        ExMap.map(),
        ExCall.call("Jason", "decode!", ExVar.var("body")));
  }

  static ExExpr decodeJsonExpr(
      Model model, SymbolProvider sp, HttpBindingIndex httpIndex, MemberShape member, ExExpr raw) {
    Shape target = model.expectShape(member.getTarget());
    if (target instanceof EnumShape || target instanceof IntEnumShape) {
      return ExCallLocal.callLocal("decode_" + helperName(sp, target), raw);
    }
    if (target instanceof UnionShape) {
      return ExCallLocal.callLocal("decode_" + helperName(sp, target), raw);
    }
    if (target instanceof StructureShape) {
      return ExCallLocal.callLocal("decode_" + helperName(sp, target), raw);
    }
    if (target instanceof TimestampShape) {
      return ExCallLocal.callLocal(timestampDecodeHelper(httpIndex, member), raw);
    }
    if (target instanceof ListShape listShape) {
      Shape element = model.expectShape(listShape.getMember().getTarget());
      if (element instanceof StructureShape) {
        return ExCallLocal.callLocal("decode_" + helperName(sp, element) + "_list", raw);
      }
      String helper = target.hasTrait(SparseTrait.class) ? "decode_sparse_list" : "decode_list";
      return ExCallLocal.callLocal(helper, raw);
    }
    if (target instanceof MapShape) {
      if (target.hasTrait(SparseTrait.class)) {
        return ExCallLocal.callLocal("decode_sparse_map", raw);
      }
      return raw;
    }
    return raw;
  }

  static ExExpr encodeJsonExpr(
      Model model,
      SymbolProvider sp,
      HttpBindingIndex httpIndex,
      MemberShape member,
      ExExpr binding) {
    Shape target = model.expectShape(member.getTarget());
    if (target instanceof EnumShape || target instanceof IntEnumShape) {
      return ExCallLocal.callLocal("encode_" + helperName(sp, target), binding);
    }
    if (target instanceof UnionShape) {
      return ExCallLocal.callLocal("encode_" + helperName(sp, target), binding);
    }
    if (target instanceof StructureShape) {
      return ExCallLocal.callLocal("encode_" + helperName(sp, target), binding);
    }
    if (target instanceof TimestampShape) {
      return ExCallLocal.callLocal(timestampEncodeHelper(httpIndex, member), binding);
    }
    if (target instanceof ListShape listShape) {
      Shape element = model.expectShape(listShape.getMember().getTarget());
      if (element instanceof StructureShape) {
        return ExCallLocal.callLocal("encode_" + helperName(sp, element) + "_list", binding);
      }
      if (target.hasTrait(SparseTrait.class)) {
        return ExCallLocal.callLocal("encode_sparse_list", binding);
      }
      return binding;
    }
    if (target instanceof MapShape) {
      if (target.hasTrait(SparseTrait.class)) {
        return ExCallLocal.callLocal("encode_sparse_map", binding);
      }
      return binding;
    }
    return binding;
  }

  static ExPipeline rejectNilMapPipeline(String bindingVar, List<ExMapEntry> entries) {
    return ExPipeline.pipeline(
        bindingVar,
        new ExMap(entries),
        ExCall.call(
            "Enum",
            "reject",
            ExAnonymousFn.compactFn(
                ExClause.inlineClause(
                    List.of(ExTuplePattern.tuple(ExVarPattern.var("_"), ExVarPattern.var("v"))),
                    ExCall.call("Kernel", "is_nil", ExVar.var("v"))))),
        ExCall.call("Map", "new"));
  }

  private static String helperName(SymbolProvider sp, Shape shape) {
    return BeamNameUtils.toSnakeCase(shape.getId().getName());
  }

  private static String fieldName(SymbolProvider sp, MemberShape member) {
    Symbol sym = sp.toSymbol(member);
    return sym.getProperty("fieldName", String.class)
        .orElseGet(() -> BeamNameUtils.toSnakeCase(member.getMemberName()));
  }

  private static String timestampEncodeHelper(HttpBindingIndex httpIndex, MemberShape member) {
    TimestampFormatTrait.Format fmt =
        httpIndex.determineTimestampFormat(
            member, HttpBinding.Location.DOCUMENT, TimestampFormatTrait.Format.DATE_TIME);
    return fmt == TimestampFormatTrait.Format.EPOCH_SECONDS
        ? "encode_timestamp_epoch_seconds"
        : "encode_timestamp_date_time";
  }

  private static String timestampDecodeHelper(HttpBindingIndex httpIndex, MemberShape member) {
    TimestampFormatTrait.Format fmt =
        httpIndex.determineTimestampFormat(
            member, HttpBinding.Location.DOCUMENT, TimestampFormatTrait.Format.DATE_TIME);
    return fmt == TimestampFormatTrait.Format.EPOCH_SECONDS
        ? "decode_timestamp_epoch_seconds"
        : "decode_timestamp_date_time";
  }
}
