package io.smithy.beam.elixir;

import io.beam.dsl.elixir.AnonFun;
import io.beam.dsl.elixir.AnonFunClause;
import io.beam.dsl.elixir.DotCallExpr;
import io.beam.dsl.elixir.Expression;
import io.beam.dsl.elixir.IfExpr;
import io.beam.dsl.elixir.InfixExpr;
import io.beam.dsl.elixir.LocalCallExpr;
import io.beam.dsl.elixir.MapEntry;
import io.beam.dsl.elixir.MapExpr;
import io.beam.dsl.elixir.MatchExpr;
import io.beam.dsl.elixir.PipeExpr;
import io.beam.dsl.elixir.PipeStep;
import io.beam.dsl.elixir.RemoteCallExpr;
import io.beam.dsl.elixir.StringExpr;
import io.beam.dsl.elixir.TuplePattern;
import io.beam.dsl.elixir.Variable;
import io.beam.dsl.elixir.VariablePattern;
import io.smithy.beam.core.BeamEventStreamIndex;
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
import software.amazon.smithy.model.traits.TimestampFormatTrait;

final class ElixirJsonCodecDsl {
  private ElixirJsonCodecDsl() {}

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

  static List<MapEntry> bodyMapEntries(
      Model model,
      HttpBindingIndex httpIndex,
      SymbolProvider sp,
      String typesMod,
      List<MemberShape> members,
      String recordVar,
      String eventStreamModule) {
    List<MapEntry> entries = new ArrayList<>();
    for (MemberShape member : members) {
      String fieldName = fieldName(sp, member);
      Shape target = model.expectShape(member.getTarget());
      if (target instanceof UnionShape union
          && BeamEventStreamIndex.of(model).isEventStreamUnion(union)) {
        String helper = ElixirEventStreamEmitter.helperName(sp, union);
        entries.add(
            MapEntry.stringKey(
                jsonKey(member),
                RemoteCallExpr.of(
                    eventStreamModule,
                    "encode_" + helper,
                    List.of(DotCallExpr.of(Variable.of(recordVar), fieldName, List.of())))));
      } else {
        entries.add(
            MapEntry.stringKey(
                jsonKey(member),
                encodeJsonExpr(
                    model,
                    sp,
                    httpIndex,
                    member,
                    DotCallExpr.of(Variable.of(recordVar), fieldName, List.of()))));
      }
    }
    return entries;
  }

  static List<MapEntry> structFieldEntriesFromDecoded(
      Model model,
      HttpBindingIndex httpIndex,
      SymbolProvider sp,
      String typesMod,
      List<MemberShape> members,
      String eventStreamModule) {
    List<MapEntry> fields = new ArrayList<>();
    for (MemberShape member : members) {
      String fieldName = fieldName(sp, member);
      Shape target = model.expectShape(member.getTarget());
      if (target instanceof UnionShape union
          && BeamEventStreamIndex.of(model).isEventStreamUnion(union)) {
        String helper = ElixirEventStreamEmitter.helperName(sp, union);
        fields.add(
            MapEntry.atomKey(
                fieldName,
                RemoteCallExpr.of(
                    eventStreamModule, "decode_" + helper, List.of(Variable.of("body")))));
      } else {
        Expression raw =
            RemoteCallExpr.of(
                "Map", "get", List.of(Variable.of("decoded"), StringExpr.of(jsonKey(member))));
        fields.add(MapEntry.atomKey(fieldName, decodeJsonExpr(model, sp, httpIndex, member, raw)));
      }
    }
    return fields;
  }

  static List<Expression> decodedBodyPrelude() {
    return List.of(MatchExpr.bind("decoded", decodedBodyExpr()));
  }

  static Expression decodedBodyExpr() {
    return IfExpr.of(
        InfixExpr.of(
            InfixExpr.of(Variable.of("body"), "==", StringExpr.of("")),
            "or",
            RemoteCallExpr.of("Kernel", "is_nil", List.of(Variable.of("body")))),
        MapExpr.of(List.of()),
        RemoteCallExpr.of("Jason", "decode!", List.of(Variable.of("body"))),
        false);
  }

  static Expression decodeJsonExpr(
      Model model,
      SymbolProvider sp,
      HttpBindingIndex httpIndex,
      MemberShape member,
      Expression raw) {
    Shape target = model.expectShape(member.getTarget());
    if (target instanceof EnumShape || target instanceof IntEnumShape) {
      return LocalCallExpr.of("decode_" + helperName(sp, target), List.of(raw));
    }
    if (target instanceof UnionShape) {
      return LocalCallExpr.of("decode_" + helperName(sp, target), List.of(raw));
    }
    if (target instanceof StructureShape) {
      return LocalCallExpr.of("decode_" + helperName(sp, target), List.of(raw));
    }
    if (target instanceof TimestampShape) {
      return LocalCallExpr.of(timestampDecodeHelper(httpIndex, member), List.of(raw));
    }
    if (target instanceof ListShape listShape) {
      Shape element = model.expectShape(listShape.getMember().getTarget());
      if (element instanceof StructureShape) {
        return LocalCallExpr.of("decode_" + helperName(sp, element) + "_list", List.of(raw));
      }
      if (element instanceof EnumShape || element instanceof IntEnumShape) {
        return LocalCallExpr.of("decode_" + helperName(sp, element) + "_list", List.of(raw));
      }
      String helper = target.hasTrait(SparseTrait.class) ? "decode_sparse_list" : "decode_list";
      return LocalCallExpr.of(helper, List.of(raw));
    }
    if (target instanceof MapShape mapShape) {
      if (ElixirMapHelperDsl.mapNeedsTypedHelper(model, mapShape)) {
        return LocalCallExpr.of(
            "decode_" + ElixirMapHelperDsl.mapHelperName(mapShape), List.of(raw));
      }
      if (mapShape.hasTrait(SparseTrait.class)) {
        return LocalCallExpr.of("decode_sparse_map", List.of(raw));
      }
      return raw;
    }
    return raw;
  }

  static Expression encodeJsonExpr(
      Model model,
      SymbolProvider sp,
      HttpBindingIndex httpIndex,
      MemberShape member,
      Expression binding) {
    Shape target = model.expectShape(member.getTarget());
    if (target instanceof EnumShape || target instanceof IntEnumShape) {
      return LocalCallExpr.of("encode_" + helperName(sp, target), List.of(binding));
    }
    if (target instanceof UnionShape) {
      return LocalCallExpr.of("encode_" + helperName(sp, target), List.of(binding));
    }
    if (target instanceof StructureShape) {
      return LocalCallExpr.of("encode_" + helperName(sp, target), List.of(binding));
    }
    if (target instanceof TimestampShape) {
      return LocalCallExpr.of(timestampEncodeHelper(httpIndex, member), List.of(binding));
    }
    if (target instanceof ListShape listShape) {
      Shape element = model.expectShape(listShape.getMember().getTarget());
      if (element instanceof StructureShape) {
        return LocalCallExpr.of("encode_" + helperName(sp, element) + "_list", List.of(binding));
      }
      if (element instanceof EnumShape || element instanceof IntEnumShape) {
        return LocalCallExpr.of("encode_" + helperName(sp, element) + "_list", List.of(binding));
      }
      if (target.hasTrait(SparseTrait.class)) {
        return LocalCallExpr.of("encode_sparse_list", List.of(binding));
      }
      return binding;
    }
    if (target instanceof MapShape mapShape) {
      if (ElixirMapHelperDsl.mapNeedsTypedHelper(model, mapShape)) {
        return LocalCallExpr.of(
            "encode_" + ElixirMapHelperDsl.mapHelperName(mapShape), List.of(binding));
      }
      if (mapShape.hasTrait(SparseTrait.class)) {
        return LocalCallExpr.of("encode_sparse_map", List.of(binding));
      }
      return binding;
    }
    return binding;
  }

  static Expression rejectNilMapPipeline(String bindingVar, List<MapEntry> entries) {
    return PipeExpr.of(
        MapExpr.of(entries),
        List.of(
            PipeStep.of(
                RemoteCallExpr.of(
                    "Enum",
                    "reject",
                    List.of(
                        AnonFun.of(
                            List.of(
                                AnonFunClause.of(
                                    List.of(
                                        TuplePattern.of(
                                            List.of(
                                                VariablePattern.of("_"), VariablePattern.of("v")))),
                                    LocalCallExpr.of("is_nil", List.of(Variable.of("v")))))))),
                List.of()),
            PipeStep.of(RemoteCallExpr.of("Map", "new", List.of()), List.of())));
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
