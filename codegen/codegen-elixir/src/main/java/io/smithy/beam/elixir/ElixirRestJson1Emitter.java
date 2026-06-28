package io.smithy.beam.elixir;

import io.smithy.beam.core.BeamElixirLayout;
import io.smithy.beam.core.BeamEventStreamIndex;
import io.smithy.beam.core.BeamHostLabelIndex;
import io.smithy.beam.core.BeamHttpBindings;
import io.smithy.beam.core.BeamNameUtils;
import io.smithy.beam.core.BeamRequestCompressionIndex;
import io.smithy.beam.ir.elixir.ExFunction;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import software.amazon.smithy.codegen.core.Symbol;
import software.amazon.smithy.codegen.core.SymbolProvider;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.knowledge.HttpBinding;
import software.amazon.smithy.model.knowledge.HttpBindingIndex;
import software.amazon.smithy.model.neighbor.Walker;
import software.amazon.smithy.model.pattern.SmithyPattern;
import software.amazon.smithy.model.shapes.BlobShape;
import software.amazon.smithy.model.shapes.EnumShape;
import software.amazon.smithy.model.shapes.IntEnumShape;
import software.amazon.smithy.model.shapes.ListShape;
import software.amazon.smithy.model.shapes.MapShape;
import software.amazon.smithy.model.shapes.MemberShape;
import software.amazon.smithy.model.shapes.OperationShape;
import software.amazon.smithy.model.shapes.ServiceShape;
import software.amazon.smithy.model.shapes.Shape;
import software.amazon.smithy.model.shapes.ShapeId;
import software.amazon.smithy.model.shapes.StructureShape;
import software.amazon.smithy.model.shapes.TimestampShape;
import software.amazon.smithy.model.shapes.UnionShape;
import software.amazon.smithy.model.traits.EndpointTrait;
import software.amazon.smithy.model.traits.EnumValueTrait;
import software.amazon.smithy.model.traits.HttpErrorTrait;
import software.amazon.smithy.model.traits.HttpTrait;
import software.amazon.smithy.model.traits.IdempotencyTokenTrait;
import software.amazon.smithy.model.traits.JsonNameTrait;
import software.amazon.smithy.model.traits.MediaTypeTrait;
import software.amazon.smithy.model.traits.SparseTrait;
import software.amazon.smithy.model.traits.StreamingTrait;
import software.amazon.smithy.model.traits.TimestampFormatTrait;

/**
 * REST JSON 1 codec emitter for Elixir. Generates encode_request and decode_response functions per
 * operation using Jason for JSON and Req for HTTP.
 */
public final class ElixirRestJson1Emitter {

  private ElixirRestJson1Emitter() {}

  public static void emitServerCodecModule(ElixirContext ctx, ServiceShape service) {
    Model model = ctx.model();
    ShapeId protocol = ctx.resolvedProtocolTraitId();
    BeamElixirLayout layout =
        new BeamElixirLayout(ctx.settings(), service.getId().getNamespace(), service);
    ElixirRuntimeHelpersEmitter.emitIfNeeded(ctx, service);
    HttpBindingIndex httpIndex = HttpBindingIndex.of(model);
    SymbolProvider sp = ctx.symbolProvider();
    List<OperationShape> operations = ElixirTopDown.containedOperationsSorted(model, service);
    boolean checksumBindings =
        ElixirHttpChecksumEmitter.serviceHasChecksumOperations(model, service);
    boolean compressionBindings = serviceHasCompressionOperations(model, service);

    String serverCodecModule =
        ElixirSymbolProvider.toModuleName(layout.serverCodecModuleName(protocol));
    String runtimeMod = ElixirSymbolProvider.toModuleName(layout.runtimeTypesModuleName());
    String typesMod = ElixirSymbolProvider.toModuleName(layout.typesModuleName());

    ctx.writerDelegator()
        .useFileWriter(
            layout.serverCodecModuleName(protocol) + ".ex",
            writer -> {
              writer.write("defmodule $L do", serverCodecModule);
              writer.indent();
              writer.write(
                  "@moduledoc \"Server REST JSON 1 codecs for $L (generated). Do not edit.\"",
                  service.getId());
              writer.write("alias $L, as: RuntimeTypes", runtimeMod);
              writer.write("alias $L, as: Types", typesMod);
              writer.write("");

              for (OperationShape op : operations) {
                emitRequestDecoder(
                    writer, model, op, httpIndex, sp, typesMod, runtimeMod, layout);
                emitResponseEncoder(writer, model, op, httpIndex, sp, typesMod, runtimeMod);
              }

              emitStructureHelpers(writer, model, service, sp);
              emitEnumHelpers(writer, model, service, sp);
              emitUnionHelpers(writer, model, service, sp);
              emitHelpers(writer, checksumBindings, false);

              writer.dedent();
              writer.write("end");
            });
  }

  public static void emitCodecModule(ElixirContext ctx, ServiceShape service) {
    Model model = ctx.model();
    ShapeId protocol = ctx.resolvedProtocolTraitId();
    BeamElixirLayout layout =
        new BeamElixirLayout(ctx.settings(), service.getId().getNamespace(), service);
    HttpBindingIndex httpIndex = HttpBindingIndex.of(model);
    SymbolProvider sp = ctx.symbolProvider();
    String moduleName = ElixirSymbolProvider.toModuleName(layout.clientCodecModuleName(protocol));
    String codecFile = layout.clientCodecModuleName(protocol) + ".ex";
    String runtimeMod = ElixirSymbolProvider.toModuleName(layout.runtimeTypesModuleName());
    String typesMod = ElixirSymbolProvider.toModuleName(layout.typesModuleName());
    List<OperationShape> operations = ElixirTopDown.containedOperationsSorted(model, service);
    boolean encodeWithConfig = serviceHasHostLabelOperations(model, service);
    boolean checksumBindings =
        ElixirHttpChecksumEmitter.serviceHasChecksumOperations(model, service);
    boolean compressionBindings = serviceHasCompressionOperations(model, service);

    ctx.writerDelegator()
        .useFileWriter(
            codecFile,
            writer -> {
              writer.write("defmodule $L do", moduleName);
              writer.indent();
              writer.write(
                  "@moduledoc \"REST JSON 1 codecs for $L (generated). Do not edit.\"",
                  service.getId());
              writer.write("alias $L, as: RuntimeTypes", runtimeMod);
              writer.write("alias $L, as: Types", typesMod);
              writer.write("");

              for (OperationShape op : operations) {
                emitEncoder(
                    writer,
                    model,
                    service,
                    op,
                    httpIndex,
                    sp,
                    typesMod,
                    runtimeMod,
                    encodeWithConfig,
                    layout);
                emitRequestDecoder(
                    writer, model, op, httpIndex, sp, typesMod, runtimeMod, layout);
                emitDecoder(writer, model, service, op, httpIndex, sp, typesMod, runtimeMod);
              }

              for (OperationShape op : operations) {
                emitErrorDispatch(writer, model, op, sp, typesMod);
              }

              emitStructureHelpers(writer, model, service, sp);
              emitEnumHelpers(writer, model, service, sp);
              emitUnionHelpers(writer, model, service, sp);
              emitHelpers(writer, checksumBindings, compressionBindings);
              if (encodeWithConfig) {
                emitBuildHostHelpers(writer, model, service, sp);
              }

              writer.dedent();
              writer.write("end");
            });
  }

  private static void emitEncoder(
      ElixirWriter writer,
      Model model,
      ServiceShape service,
      OperationShape op,
      HttpBindingIndex httpIndex,
      SymbolProvider sp,
      String typesMod,
      String runtimeMod,
      boolean encodeWithConfig,
      BeamElixirLayout layout) {
    String eventStreamModule =
        ElixirSymbolProvider.toModuleName(layout.eventStreamModuleName());
    ExFunction fn =
        ElixirRestJsonOperationIr.buildEncodeRequest(
            model,
            service,
            op,
            httpIndex,
            sp,
            typesMod,
            runtimeMod,
            encodeWithConfig,
            eventStreamModule);
    writer.write("$L", fn.asString());
    writer.write("");
  }

  private static void emitResponseEncoder(
      ElixirWriter writer,
      Model model,
      OperationShape op,
      HttpBindingIndex httpIndex,
      SymbolProvider sp,
      String typesMod,
      String runtimeMod) {
    ExFunction fn =
        ElixirRestJsonOperationIr.buildEncodeResponse(
            model, op, httpIndex, sp, typesMod, runtimeMod);
    writer.write("$L", fn.asString());
    writer.write("");
  }

  private static void emitRequestDecoder(
      ElixirWriter writer,
      Model model,
      OperationShape op,
      HttpBindingIndex httpIndex,
      SymbolProvider sp,
      String typesMod,
      String runtimeMod,
      BeamElixirLayout layout) {
    String eventStreamModule =
        ElixirSymbolProvider.toModuleName(layout.eventStreamModuleName());
    ExFunction fn =
        ElixirRestJsonOperationIr.buildDecodeRequest(
            model, op, httpIndex, sp, typesMod, runtimeMod, eventStreamModule);
    writer.write("$L", fn.asString());
    writer.write("");
  }

  private static void emitDecoder(
      ElixirWriter writer,
      Model model,
      ServiceShape service,
      OperationShape op,
      HttpBindingIndex httpIndex,
      SymbolProvider sp,
      String typesMod,
      String runtimeMod) {
    ExFunction fn =
        ElixirRestJsonOperationIr.buildDecodeResponse(
            model, service, op, httpIndex, sp, typesMod, runtimeMod);
    writer.write("$L", fn.asString());
    writer.write("");
  }

  private static void emitErrorDispatch(
      ElixirWriter writer, Model model, OperationShape op, SymbolProvider sp, String typesMod) {
    ExFunction fn = ElixirRestJsonOperationIr.buildErrorDispatch(model, op, sp, typesMod);
    writer.write("$L", fn.asString());
    writer.write("");
  }

  private static void emitEnumHelpers(
      ElixirWriter writer, Model model, ServiceShape service, SymbolProvider sp) {

    Set<ShapeId> emitted = new LinkedHashSet<>();
    for (Shape shape : new Walker(model).walkShapes(service)) {
      if (shape instanceof EnumShape enumShape) {
        if (emitted.add(enumShape.getId())) {
          emitElixirEnumHelpers(writer, enumShape, sp);
        }
      } else if (shape instanceof IntEnumShape intEnumShape) {
        if (emitted.add(intEnumShape.getId())) {
          emitElixirIntEnumHelpers(writer, intEnumShape, sp);
        }
      }
    }
  }

  private static void collectEnumTarget(Model model, MemberShape member, Set<ShapeId> out) {
    Shape target = model.expectShape(member.getTarget());
    if (target instanceof EnumShape || target instanceof IntEnumShape) {
      out.add(target.getId());
    }
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

  private static void emitStructureHelpers(
      ElixirWriter writer, Model model, ServiceShape service, SymbolProvider sp) {

    Set<ShapeId> emitted = new LinkedHashSet<>();
    Set<ShapeId> listElementStructures = new LinkedHashSet<>();
    HttpBindingIndex httpIndex = HttpBindingIndex.of(model);

    for (OperationShape op : ElixirTopDown.containedOperationsSorted(model, service)) {
      StructureShape input = model.expectShape(op.getInputShape(), StructureShape.class);
      for (MemberShape member : documentMembers(httpIndex, op, input, true)) {
        collectStructureTargets(model, member, emitted, listElementStructures);
      }
      StructureShape output = model.expectShape(op.getOutputShape(), StructureShape.class);
      for (MemberShape member : documentMembers(httpIndex, op, output, false)) {
        collectStructureTargets(model, member, emitted, listElementStructures);
      }
      for (HttpBinding.Location loc : HttpBinding.Location.values()) {
        for (HttpBinding b : httpIndex.getRequestBindings(op, loc)) {
          collectStructureTargets(model, b.getMember(), emitted, listElementStructures);
        }
        for (HttpBinding b : httpIndex.getResponseBindings(op, loc)) {
          collectStructureTargets(model, b.getMember(), emitted, listElementStructures);
        }
      }
    }

    for (ShapeId structureId : emitted) {
      StructureShape structure = model.expectShape(structureId, StructureShape.class);
      emitStructureDecodeEncode(writer, model, httpIndex, structure, sp);
      if (listElementStructures.contains(structureId)) {
        emitStructureListDecodeEncode(writer, structure, sp);
      }
    }
  }

  private static void collectStructureTargets(
      Model model, MemberShape member, Set<ShapeId> out, Set<ShapeId> listElements) {
    Shape target = model.expectShape(member.getTarget());
    if (target instanceof StructureShape structure) {
      if (out.add(structure.getId())) {
        for (MemberShape nested : structure.members()) {
          collectStructureTargets(model, nested, out, listElements);
        }
      }
    } else if (target instanceof ListShape list) {
      Shape element = model.expectShape(list.getMember().getTarget());
      if (element instanceof StructureShape structure) {
        listElements.add(structure.getId());
      }
      collectStructureTargets(model, list.getMember(), out, listElements);
    } else if (target instanceof MapShape map) {
      collectStructureTargets(model, map.getValue(), out, listElements);
    }
  }

  private static void emitStructureDecodeEncode(
      ElixirWriter writer,
      Model model,
      HttpBindingIndex httpIndex,
      StructureShape structure,
      SymbolProvider sp) {
    String helperName = structureHelperName(structure);
    String structName = sp.toSymbol(structure).getName();
    writer.write("# Structure helpers for $L", structure.getId());
    writer.write("defp decode_$L(nil), do: nil", helperName);
    writer.write("defp decode_$L(map) when is_map(map) do", helperName);
    writer.indent();
    writer.write("%Types.$L{", structName);
    int totalFields = structure.members().size();
    int fieldIndex = 0;
    for (MemberShape member : structure.members()) {
      fieldIndex++;
      String field = fieldName(sp, member);
      String wireKey = jsonKey(member);
      String suffix = fieldIndex < totalFields ? "," : "";
      writer.write(
          "  $L: $L$L",
          field,
          decodeJsonValue(model, sp, httpIndex, member, "Map.get(map, \"" + wireKey + "\")"),
          suffix);
    }
    writer.write("}");
    writer.dedent();
    writer.write("end");
    writer.write("");

    writer.write("defp encode_$L(nil), do: nil", helperName);
    writer.write("defp encode_$L(%Types.$L{} = record) do", helperName, structName);
    writer.indent();
    writer.write("%{");
    fieldIndex = 0;
    for (MemberShape member : structure.members()) {
      fieldIndex++;
      String field = fieldName(sp, member);
      String wireKey = jsonKey(member);
      String suffix = fieldIndex < totalFields ? "," : "";
      writer.write(
          "  \"$L\" => $L$L",
          wireKey,
          encodeJsonValue(model, sp, httpIndex, member, "record." + field),
          suffix);
    }
    writer.write("}");
    writer.write("|> Enum.reject(fn {_k, v} -> is_nil(v) end)");
    writer.write("|> Map.new()");
    writer.dedent();
    writer.write("end");
    writer.write("");
  }

  private static void emitStructureListDecodeEncode(
      ElixirWriter writer, StructureShape structure, SymbolProvider sp) {
    String helperName = structureHelperName(structure);
    writer.write("defp decode_$L_list(nil), do: nil", helperName);
    writer.write("defp decode_$L_list(list) when is_list(list),", helperName);
    writer.write("  do: Enum.map(list, fn v -> decode_$L(v) end)", helperName);
    writer.write("");
    writer.write("defp encode_$L_list(nil), do: nil", helperName);
    writer.write("defp encode_$L_list(list) when is_list(list),", helperName);
    writer.write("  do: Enum.map(list, fn v -> encode_$L(v) end)", helperName);
    writer.write("");
  }

  private static String structureHelperName(Shape shape) {
    return BeamNameUtils.toSnakeCase(shape.getId().getName());
  }

  static String decodeDocumentValue(
      Model model,
      SymbolProvider sp,
      HttpBindingIndex httpIndex,
      MemberShape member,
      String jsonKey) {
    return decodeJsonValue(model, sp, httpIndex, member, "Map.get(decoded, \"" + jsonKey + "\")");
  }

  private static String decodeJsonValue(
      Model model, SymbolProvider sp, HttpBindingIndex httpIndex, MemberShape member, String raw) {
    Shape target = model.expectShape(member.getTarget());
    if (target instanceof EnumShape || target instanceof IntEnumShape) {
      return "decode_" + enumHelperName(target) + "(" + raw + ")";
    }
    if (target instanceof UnionShape) {
      return "decode_" + unionHelperName(target) + "(" + raw + ")";
    }
    if (target instanceof StructureShape) {
      return "decode_" + structureHelperName(target) + "(" + raw + ")";
    }
    if (target instanceof TimestampShape) {
      return timestampDecodeHelper(httpIndex, member, HttpBinding.Location.DOCUMENT)
          + "("
          + raw
          + ")";
    }
    if (target instanceof ListShape listShape) {
      Shape element = model.expectShape(listShape.getMember().getTarget());
      if (element instanceof StructureShape) {
        return "decode_" + structureHelperName(element) + "_list(" + raw + ")";
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
      return "encode_" + enumHelperName(target) + "(" + binding + ")";
    }
    if (target instanceof UnionShape) {
      return "encode_" + unionHelperName(target) + "(" + binding + ")";
    }
    if (target instanceof StructureShape) {
      return "encode_" + structureHelperName(target) + "(" + binding + ")";
    }
    if (target instanceof TimestampShape) {
      return timestampEncodeHelper(httpIndex, member, HttpBinding.Location.DOCUMENT)
          + "("
          + binding
          + ")";
    }
    if (target instanceof ListShape listShape) {
      Shape element = model.expectShape(listShape.getMember().getTarget());
      if (element instanceof StructureShape) {
        return "encode_" + structureHelperName(element) + "_list(" + binding + ")";
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

  static String encodeDocumentEntry(
      Model model,
      SymbolProvider sp,
      HttpBindingIndex httpIndex,
      MemberShape member,
      String binding,
      String wireKey) {
    return "\"" + wireKey + "\" => " + encodeJsonValue(model, sp, httpIndex, member, binding);
  }

  private static void emitUnionHelpers(
      ElixirWriter writer, Model model, ServiceShape service, SymbolProvider sp) {

    BeamEventStreamIndex eventStreamIndex = BeamEventStreamIndex.of(model);
    Set<ShapeId> emitted = new LinkedHashSet<>();
    for (Shape shape : new Walker(model).walkShapes(service)) {
      if (shape instanceof UnionShape union
          && !eventStreamIndex.isEventStreamUnion(union)
          && emitted.add(union.getId())) {
        emitElixirUnionHelpers(writer, union, sp);
      }
    }
  }

  private static void emitElixirUnionHelpers(
      ElixirWriter writer, UnionShape shape, SymbolProvider sp) {
    String helperName = unionHelperName(shape);
    writer.write("# Union helpers for $L", shape.getId());
    writer.write("defp decode_$L(map) when is_map(map) do", helperName);
    writer.indent();
    writer.write("case Map.to_list(map) do");
    writer.indent();
    for (MemberShape m : shape.members()) {
      String wireKey = m.getMemberName();
      String tag = unionTagForMember(sp, m);
      writer.write("[{\"$L\", v}] -> {$L, v}", wireKey, tag);
    }
    writer.write("[{k, _v}] -> {:unknown, k}");
    writer.write("_ -> nil");
    writer.dedent();
    writer.write("end");
    writer.dedent();
    writer.write("end");
    writer.write("defp decode_$L(nil), do: nil", helperName);
    writer.write("");

    for (MemberShape m : shape.members()) {
      String wireKey = m.getMemberName();
      String tag = unionTagForMember(sp, m);
      writer.write("defp encode_$L({$L, v}), do: %{\"$L\" => v}", helperName, tag, wireKey);
    }
    writer.write("defp encode_$L({:unknown, k}) when is_binary(k), do: %{k => nil}", helperName);
    writer.write("defp encode_$L(nil), do: nil", helperName);
    writer.write("");
  }

  private static String unionHelperName(Shape shape) {
    return BeamNameUtils.toSnakeCase(shape.getId().getName());
  }

  private static String unionTagForMember(SymbolProvider sp, MemberShape member) {
    return ":" + sp.toSymbol(member).getProperty("unionTag", String.class).orElseThrow();
  }

  private static void emitElixirEnumHelpers(
      ElixirWriter writer, EnumShape shape, SymbolProvider sp) {
    String helperName = enumHelperName(shape);
    writer.write("# Enum helpers for $L", shape.getId());
    for (MemberShape m : shape.members()) {
      String wireValue =
          m.getTrait(EnumValueTrait.class)
              .flatMap(EnumValueTrait::getStringValue)
              .orElse(m.getMemberName());
      String atom = ":" + enumAtomForMember(sp, shape, m.getMemberName());
      writer.write("defp decode_$L(\"$L\"), do: $L", helperName, wireValue, atom);
    }
    writer.write("defp decode_$L(v) when is_binary(v), do: {:unknown, v}", helperName);
    writer.write("defp decode_$L(nil), do: nil", helperName);
    writer.write("");
    for (MemberShape m : shape.members()) {
      String wireValue =
          m.getTrait(EnumValueTrait.class)
              .flatMap(EnumValueTrait::getStringValue)
              .orElse(m.getMemberName());
      String atom = ":" + enumAtomForMember(sp, shape, m.getMemberName());
      writer.write("defp encode_$L($L), do: \"$L\"", helperName, atom, wireValue);
    }
    writer.write("defp encode_$L({:unknown, v}) when is_binary(v), do: v", helperName);
    writer.write("defp encode_$L(nil), do: nil", helperName);
    writer.write("");
  }

  private static void emitElixirIntEnumHelpers(
      ElixirWriter writer, IntEnumShape shape, SymbolProvider sp) {
    String helperName = enumHelperName(shape);
    writer.write("# IntEnum helpers for $L", shape.getId());
    for (MemberShape m : shape.members()) {
      int wireValue = m.expectTrait(EnumValueTrait.class).expectIntValue();
      String atom = ":" + enumAtomForMember(sp, shape, m.getMemberName());
      writer.write("defp decode_$L($L), do: $L", helperName, wireValue, atom);
    }
    writer.write("defp decode_$L(v) when is_integer(v), do: {:unknown, v}", helperName);
    writer.write("defp decode_$L(nil), do: nil", helperName);
    writer.write("");
    for (MemberShape m : shape.members()) {
      int wireValue = m.expectTrait(EnumValueTrait.class).expectIntValue();
      String atom = ":" + enumAtomForMember(sp, shape, m.getMemberName());
      writer.write("defp encode_$L($L), do: $L", helperName, atom, wireValue);
    }
    writer.write("defp encode_$L({:unknown, v}) when is_integer(v), do: v", helperName);
    writer.write("defp encode_$L(nil), do: nil", helperName);
    writer.write("");
  }

  private static String enumHelperName(Shape shape) {
    return BeamNameUtils.toSnakeCase(shape.getId().getName());
  }

  private static String enumAtomForMember(SymbolProvider sp, Shape enumShape, String memberName) {
    @SuppressWarnings("unchecked")
    Map<String, String> byMember =
        sp.toSymbol(enumShape).getProperty("enumAtomByMember", Map.class).orElseThrow();
    return byMember.get(memberName);
  }

  static void emitSharedCodecHelpers(
      ElixirWriter writer, Model model, ServiceShape service, SymbolProvider sp) {
    boolean checksumBindings =
        ElixirHttpChecksumEmitter.serviceHasChecksumOperations(model, service);
    boolean compressionBindings = serviceHasCompressionOperations(model, service);
    emitStructureHelpers(writer, model, service, sp);
    emitEnumHelpers(writer, model, service, sp);
    emitUnionHelpers(writer, model, service, sp);
    emitHelpers(writer, checksumBindings, compressionBindings);
  }

  private static boolean serviceHasCompressionOperations(Model model, ServiceShape service) {
    for (OperationShape op : ElixirTopDown.containedOperationsSorted(model, service)) {
      if (supportsGzipCompression(op)) {
        return true;
      }
    }
    return false;
  }

  private static boolean supportsGzipCompression(OperationShape op) {
    return BeamRequestCompressionIndex.forOperation(op)
        .map(
            trait ->
                trait.getEncodings().stream()
                    .anyMatch(encoding -> encoding.equalsIgnoreCase("gzip")))
        .orElse(false);
  }

  private static void emitHelpers(
      ElixirWriter writer, boolean checksumBindings, boolean compressionBindings) {
    writer.write("# -- Private helpers --");
    writer.write("");
    writer.write("defp uri_encode(value), do: URI.encode(to_string(value))");
    writer.write("");
    writer.write("defp uri_decode(nil), do: nil");
    writer.write("defp uri_decode(value), do: URI.decode(value)");
    writer.write("");
    writer.write("defp decode_query_param(nil), do: nil");
    writer.write("defp decode_query_param(true), do: true");
    writer.write("defp decode_query_param(false), do: false");
    writer.write("defp decode_query_param(\"true\"), do: true");
    writer.write("defp decode_query_param(\"false\"), do: false");
    writer.write("defp decode_query_param(value), do: value");
    writer.write("");
    writer.write("defp prefix_headers_to_list(_prefix, nil), do: []");
    writer.write("defp prefix_headers_to_list(prefix, map) when is_map(map) do");
    writer.indent();
    writer.write("Enum.map(map, fn {k, v} -> {prefix <> k, to_string(v)} end)");
    writer.dedent();
    writer.write("end");
    writer.write("");
    writer.write("defp prefix_headers_from_list(headers, prefix) do");
    writer.indent();
    writer.write("headers");
    writer.write("|> Enum.filter(fn {name, _} -> String.starts_with?(name, prefix) end)");
    writer.write(
        "|> Map.new(fn {name, val} -> {String.slice(name, byte_size(prefix)..-1//1), val} end)");
    writer.write("|> case do");
    writer.indent();
    writer.write("map when map == %{} -> nil");
    writer.write("map -> map");
    writer.dedent();
    writer.write("end");
    writer.dedent();
    writer.write("end");
    writer.write("");
    writer.write("defp decode_sparse_list(nil), do: nil");
    writer.write("defp decode_sparse_list(list) when is_list(list),");
    writer.write("  do:");
    writer.indent();
    writer.write("Enum.map(list, fn");
    writer.indent();
    writer.write("nil -> nil");
    writer.write("v -> v");
    writer.dedent();
    writer.write("end)");
    writer.dedent();
    writer.write("");
    writer.write("defp decode_list(nil), do: nil");
    writer.write("defp decode_list(list) when is_list(list),");
    writer.write("    do: Enum.reject(list, &is_nil/1)");
    writer.write("");
    writer.write("defp decode_sparse_map(nil), do: nil");
    writer.write("defp decode_sparse_map(map) when is_map(map),");
    writer.write("  do:");
    writer.indent();
    writer.write("Map.new(map, fn");
    writer.indent();
    writer.write("{k, nil} -> {k, nil}");
    writer.write("{k, v} -> {k, v}");
    writer.dedent();
    writer.write("end)");
    writer.dedent();
    writer.write("");
    writer.write("defp encode_timestamp_epoch_seconds(nil), do: nil");
    writer.write("defp encode_timestamp_epoch_seconds(%DateTime{} = dt),");
    writer.write("    do: DateTime.to_unix(dt)");
    writer.write("");
    writer.write("defp encode_timestamp_date_time(nil), do: nil");
    writer.write("defp encode_timestamp_date_time(%DateTime{} = dt),");
    writer.write("    do: DateTime.to_iso8601(dt)");
    writer.write("");
    writer.write("defp decode_timestamp_epoch_seconds(nil), do: nil");
    writer.write("defp decode_timestamp_epoch_seconds(v) when is_number(v),");
    writer.write("    do: DateTime.from_unix!(trunc(v))");
    writer.write("");
    writer.write("defp decode_timestamp_date_time(nil), do: nil");
    writer.write("defp decode_timestamp_date_time(v) when is_number(v),");
    writer.write("    do: DateTime.from_unix!(trunc(v))");
    writer.write("defp decode_timestamp_date_time(v) when is_binary(v) do");
    writer.indent();
    writer.write("case DateTime.from_iso8601(v) do");
    writer.indent();
    writer.write("{:ok, dt, _} -> dt");
    writer.write("_ -> nil");
    writer.dedent();
    writer.write("end");
    writer.dedent();
    writer.write("end");
    writer.write("");
    writer.write("defp generate_uuid do");
    writer.indent();
    writer.write(
        "<<a::32, b::16, _::4, c::12, _::2, d::14, e::48>> = :crypto.strong_rand_bytes(16)");
    writer.write("<<a::32, b::16, 4::4, c::12, 2::2, d::14, e::48>>");
    writer.write("|> Base.encode16(case: :lower)");
    writer.write("|> then(fn hex ->");
    writer.indent();
    writer.write("<<part_a::8, part_b::4, part_c::4, part_d::4, part_e::12>> = hex");
    writer.write("\"#{part_a}-#{part_b}-#{part_c}-#{part_d}-#{part_e}\"");
    writer.dedent();
    writer.write("end)");
    writer.dedent();
    writer.write("end");
    writer.write("");
    if (checksumBindings) {
      ElixirHttpChecksumEmitter.emitChecksumHelpers(writer);
    } else if (compressionBindings) {
      writer.write("defp headers_set(name, value, headers) do");
      writer.indent();
      writer.write("List.keystore(name, 0, headers, {name, value})");
      writer.dedent();
      writer.write("end");
      writer.write("");
    }
    emitDecodeJsonBodyHelper(writer);
  }

  private static void emitDecodeJsonBodyHelper(ElixirWriter writer) {
    writer.write("defp decode_json_body(\"\"), do: %{}");
    writer.write("defp decode_json_body(body) do");
    writer.indent();
    writer.write("case Jason.decode(body) do");
    writer.indent();
    writer.write("{:ok, map} when is_map(map) -> map");
    writer.write("_ -> %{}");
    writer.dedent();
    writer.write("end");
    writer.dedent();
    writer.write("end");
    writer.write("");
    writer.write("defp content_type_matches(headers, expected) do");
    writer.indent();
    writer.write("case List.keyfind(headers, \"Content-Type\", 0) do");
    writer.indent();
    writer.write("{_, ct} when ct == expected -> :ok");
    writer.write("{_, ct} when is_binary(ct) ->");
    writer.indent();
    writer.write(
        "if ct_base(ct) == ct_base(expected), do: :ok, else: {:error, {:invalid_content_type, ct}}");
    writer.dedent();
    writer.write("_ -> {:error, {:invalid_content_type, nil}}");
    writer.dedent();
    writer.write("end");
    writer.dedent();
    writer.write("end");
    writer.write("");
    writer.write("defp ct_base(ct) do");
    writer.indent();
    writer.write("case String.split(ct, \";\") do");
    writer.indent();
    writer.write("[base | _] -> base");
    writer.write("_ -> ct");
    writer.dedent();
    writer.write("end");
    writer.dedent();
    writer.write("end");
    writer.write("");
  }

  private static String timestampEncodeHelper(
      HttpBindingIndex httpIndex, MemberShape member, HttpBinding.Location location) {
    TimestampFormatTrait.Format fmt =
        httpIndex.determineTimestampFormat(member, location, TimestampFormatTrait.Format.DATE_TIME);
    return fmt == TimestampFormatTrait.Format.EPOCH_SECONDS
        ? "encode_timestamp_epoch_seconds"
        : "encode_timestamp_date_time";
  }

  private static String timestampDecodeHelper(
      HttpBindingIndex httpIndex, MemberShape member, HttpBinding.Location location) {
    TimestampFormatTrait.Format fmt =
        httpIndex.determineTimestampFormat(member, location, TimestampFormatTrait.Format.DATE_TIME);
    return fmt == TimestampFormatTrait.Format.EPOCH_SECONDS
        ? "decode_timestamp_epoch_seconds"
        : "decode_timestamp_date_time";
  }

  private static String fieldName(SymbolProvider sp, MemberShape member) {
    Symbol sym = sp.toSymbol(member);
    return sym.getProperty("fieldName", String.class)
        .orElseGet(() -> BeamNameUtils.toSnakeCase(member.getMemberName()));
  }

  private static String jsonKey(MemberShape member) {
    return member
        .getTrait(JsonNameTrait.class)
        .map(JsonNameTrait::getValue)
        .orElse(member.getMemberName());
  }

  private static String escapeElixirString(String value) {
    return value.replace("\\", "\\\\").replace("\"", "\\\"");
  }

  private static void emitBuildHostHelpers(
      ElixirWriter writer, Model model, ServiceShape service, SymbolProvider sp) {

    writer.write("defp split_base_url(\"\"), do: {\"\", \"\"}");
    writer.write("defp split_base_url(base_url) do");
    writer.indent();
    writer.write("case URI.parse(base_url) do");
    writer.indent();
    writer.write("%URI{scheme: scheme, host: host} = uri when is_binary(host) ->");
    writer.indent();
    writer.write("port_suffix =");
    writer.indent();
    writer.write("case {uri.scheme, uri.port} do");
    writer.indent();
    writer.write("{\"https\", 443} -> \"\"");
    writer.write("{\"http\", 80} -> \"\"");
    writer.write("{_, nil} -> \"\"");
    writer.write("{_, port} -> \":#{port}\"");
    writer.dedent();
    writer.write("end");
    writer.dedent();
    writer.write("{scheme <> \"://\", host <> port_suffix}");
    writer.dedent();
    writer.write("_ ->");
    writer.indent();
    writer.write("{\"\", base_url}");
    writer.dedent();
    writer.dedent();
    writer.write("end");
    writer.dedent();
    writer.write("end");
    writer.write("");

    BeamHostLabelIndex hostLabelIndex = BeamHostLabelIndex.of(model);
    for (OperationShape op : ElixirTopDown.containedOperationsSorted(model, service)) {
      List<MemberShape> hostLabels = hostLabelIndex.hostLabelMembers(op);
      if (hostLabels.isEmpty() || !op.hasTrait(EndpointTrait.class)) {
        continue;
      }
      StructureShape input = model.expectShape(op.getInputShape(), StructureShape.class);
      String inputStruct = sp.toSymbol(input).getName();
      SmithyPattern hostPrefix = op.expectTrait(EndpointTrait.class).getHostPrefix();
      String prefixExpr = buildElixirHostPrefixExpression(hostPrefix, hostLabels, sp);

      writer.write("defp build_host(%Types.$L{", inputStruct);
      for (int i = 0; i < hostLabels.size(); i++) {
        MemberShape member = hostLabels.get(i);
        String field = fieldName(sp, member);
        String comma = i < hostLabels.size() - 1 ? "," : "";
        writer.write("  $L: $L$L", field, field, comma);
      }
      writer.write("}, config) do");
      writer.indent();
      writer.write("base_url = Map.get(config, :base_url, \"\")");
      writer.write("{_scheme, authority} = split_base_url(base_url)");
      writer.write("prefix = $L", prefixExpr);
      writer.write("prefix <> authority");
      writer.dedent();
      writer.write("end");
      writer.write("");
    }
  }

  public static boolean serviceHasHostLabelOperations(Model model, ServiceShape service) {
    BeamHostLabelIndex hostLabelIndex = BeamHostLabelIndex.of(model);
    for (OperationShape op : ElixirTopDown.containedOperationsSorted(model, service)) {
      if (!hostLabelIndex.hostLabelMembers(op).isEmpty() && op.hasTrait(EndpointTrait.class)) {
        return true;
      }
    }
    return false;
  }

  private static String buildElixirHostPrefixExpression(
      SmithyPattern hostPrefix, List<MemberShape> hostLabels, SymbolProvider sp) {
    if (hostPrefix.getSegments().isEmpty()) {
      return "\"\"";
    }
    Map<String, String> labelFields = new HashMap<>();
    for (MemberShape member : hostLabels) {
      labelFields.put(member.getMemberName(), fieldName(sp, member));
    }
    StringBuilder sb = new StringBuilder();
    List<SmithyPattern.Segment> segments = hostPrefix.getSegments();
    for (int i = 0; i < segments.size(); i++) {
      SmithyPattern.Segment segment = segments.get(i);
      if (segment.isLabel()) {
        String field =
            labelFields.getOrDefault(
                segment.getContent(), BeamNameUtils.toSnakeCase(segment.getContent()));
        sb.append("URI.encode(to_string(").append(field).append("))");
      } else {
        sb.append("\"").append(escapeElixirString(segment.getContent())).append("\"");
      }
      if (i < segments.size() - 1) {
        sb.append(" <> ");
      }
    }
    return sb.toString();
  }
}
