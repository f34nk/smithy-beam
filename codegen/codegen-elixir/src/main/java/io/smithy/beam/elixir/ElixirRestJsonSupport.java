package io.smithy.beam.elixir;

import io.smithy.beam.core.BeamEventStreamIndex;
import io.smithy.beam.core.BeamHostLabelIndex;
import io.smithy.beam.core.BeamRequestCompressionIndex;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.knowledge.HttpBinding;
import software.amazon.smithy.model.knowledge.HttpBindingIndex;
import software.amazon.smithy.model.neighbor.Walker;
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
import software.amazon.smithy.model.shapes.UnionShape;
import software.amazon.smithy.model.traits.EndpointTrait;

final class ElixirRestJsonSupport {
  private ElixirRestJsonSupport() {}

  static List<EnumShape> reachableEnumShapes(Model model, ServiceShape service) {
    Set<ShapeId> emitted = new LinkedHashSet<>();
    List<EnumShape> shapes = new ArrayList<>();
    for (Shape shape : new Walker(model).walkShapes(service)) {
      if (shape instanceof EnumShape enumShape && emitted.add(enumShape.getId())) {
        shapes.add(enumShape);
      }
    }
    return shapes;
  }

  static List<IntEnumShape> reachableIntEnumShapes(Model model, ServiceShape service) {
    Set<ShapeId> emitted = new LinkedHashSet<>();
    List<IntEnumShape> shapes = new ArrayList<>();
    for (Shape shape : new Walker(model).walkShapes(service)) {
      if (shape instanceof IntEnumShape intEnumShape && emitted.add(intEnumShape.getId())) {
        shapes.add(intEnumShape);
      }
    }
    return shapes;
  }

  static List<UnionShape> reachableUnionShapes(Model model, ServiceShape service) {
    BeamEventStreamIndex eventStreamIndex = BeamEventStreamIndex.of(model);
    Set<ShapeId> emitted = new LinkedHashSet<>();
    List<UnionShape> shapes = new ArrayList<>();
    for (Shape shape : new Walker(model).walkShapes(service)) {
      if (shape instanceof UnionShape union
          && !eventStreamIndex.isEventStreamUnion(union)
          && emitted.add(union.getId())) {
        shapes.add(union);
      }
    }
    return shapes;
  }

  static List<MapShape> reachableTypedMapShapes(Model model, ServiceShape service) {
    Set<ShapeId> emitted = new LinkedHashSet<>();
    List<MapShape> shapes = new ArrayList<>();
    for (Shape shape : new Walker(model).walkShapes(service)) {
      if (shape instanceof MapShape mapShape
          && ElixirMapHelperIr.mapNeedsTypedHelper(model, mapShape)
          && emitted.add(mapShape.getId())) {
        shapes.add(mapShape);
      }
    }
    return shapes;
  }

  static void collectStructureHelperTargets(
      Model model,
      ServiceShape service,
      HttpBindingIndex httpIndex,
      Set<StructureShape> structures,
      Set<StructureShape> listElementStructures) {
    Set<ShapeId> emitted = new LinkedHashSet<>();
    Set<ShapeId> listElementIds = new LinkedHashSet<>();

    for (OperationShape op : ElixirTopDown.containedOperationsSorted(model, service)) {
      StructureShape input = model.expectShape(op.getInputShape(), StructureShape.class);
      for (MemberShape member : ElixirJsonCodecIr.documentMembers(httpIndex, op, input, true)) {
        collectStructureTargets(model, member, emitted, listElementIds);
      }
      StructureShape output = model.expectShape(op.getOutputShape(), StructureShape.class);
      for (MemberShape member : ElixirJsonCodecIr.documentMembers(httpIndex, op, output, false)) {
        collectStructureTargets(model, member, emitted, listElementIds);
      }
      for (HttpBinding.Location loc : HttpBinding.Location.values()) {
        for (HttpBinding b : httpIndex.getRequestBindings(op, loc)) {
          collectStructureTargets(model, b.getMember(), emitted, listElementIds);
        }
        for (HttpBinding b : httpIndex.getResponseBindings(op, loc)) {
          collectStructureTargets(model, b.getMember(), emitted, listElementIds);
        }
      }
    }

    for (ShapeId structureId : emitted) {
      structures.add(model.expectShape(structureId, StructureShape.class));
    }
    for (ShapeId structureId : listElementIds) {
      listElementStructures.add(model.expectShape(structureId, StructureShape.class));
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

  static boolean serviceHasHostLabelOperations(Model model, ServiceShape service) {
    BeamHostLabelIndex hostLabelIndex = BeamHostLabelIndex.of(model);
    for (OperationShape op : ElixirTopDown.containedOperationsSorted(model, service)) {
      if (!hostLabelIndex.hostLabelMembers(op).isEmpty() && op.hasTrait(EndpointTrait.class)) {
        return true;
      }
    }
    return false;
  }

  static boolean serviceHasCompressionOperations(Model model, ServiceShape service) {
    for (OperationShape op : ElixirTopDown.containedOperationsSorted(model, service)) {
      if (supportsGzipCompression(op)) {
        return true;
      }
    }
    return false;
  }

  static boolean supportsGzipCompression(OperationShape op) {
    return BeamRequestCompressionIndex.forOperation(op)
        .map(
            trait ->
                trait.getEncodings().stream()
                    .anyMatch(encoding -> encoding.equalsIgnoreCase("gzip")))
        .orElse(false);
  }
}
