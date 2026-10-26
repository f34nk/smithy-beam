package io.smithy.beam.gleam;

import io.beam.lang.gleam.DocComment;
import io.beam.lang.gleam.Function;
import io.beam.lang.gleam.Import;
import io.beam.lang.gleam.Module;
import io.smithy.beam.core.BeamGleamLayout;
import io.smithy.beam.core.BeamProtocolIds;
import java.util.ArrayList;
import java.util.List;
import software.amazon.smithy.codegen.core.SymbolProvider;
import software.amazon.smithy.model.Model;
import software.amazon.smithy.model.shapes.EnumShape;
import software.amazon.smithy.model.shapes.IntEnumShape;
import software.amazon.smithy.model.shapes.MapShape;
import software.amazon.smithy.model.shapes.ServiceShape;
import software.amazon.smithy.model.shapes.ShapeId;
import software.amazon.smithy.model.shapes.StructureShape;
import software.amazon.smithy.model.shapes.UnionShape;

final class GleamRestJsonDsl {

  private GleamRestJsonDsl() {}

  static void emitClientCodecModule(GleamContext ctx, ServiceShape service) {
    ShapeId protocol = ctx.resolvedProtocolTraitId();
    if (protocol == null || !BeamProtocolIds.REST_JSON_1.equals(protocol)) {
      return;
    }
    BeamGleamLayout layout =
        new BeamGleamLayout(ctx.settings(), service.getId().getNamespace(), service);
    Module module = buildClientCodecModule(ctx, service, layout);
    GleamClientEmission.writeModule(ctx, layout.codecModuleFile(protocol), module);
  }

  static Module buildClientCodecModule(
      GleamContext ctx, ServiceShape service, BeamGleamLayout layout) {
    Model model = ctx.model();
    SymbolProvider sp = ctx.symbolProvider();
    String typesModule = layout.typesModuleName();
    List<Function> functions = new ArrayList<>();

    for (EnumShape shape : GleamRestJsonSupport.reachableEnumShapes(model, service)) {
      functions.addAll(GleamEnumHelperDsl.enumHelpers(shape, sp, typesModule));
    }
    for (IntEnumShape shape : GleamRestJsonSupport.reachableIntEnumShapes(model, service)) {
      functions.addAll(GleamEnumHelperDsl.intEnumHelpers(shape, sp, typesModule));
    }
    for (UnionShape shape : GleamRestJsonSupport.reachableUnionShapes(model, service)) {
      functions.addAll(GleamUnionHelperDsl.unionHelpers(model, shape, sp, typesModule));
    }
    for (StructureShape shape : GleamRestJsonSupport.reachableStructureShapes(model, service)) {
      functions.addAll(GleamStructureHelperDsl.structureHelpers(model, shape, sp, typesModule));
    }
    for (MapShape shape : GleamRestJsonSupport.reachableTypedMapShapes(model, service)) {
      functions.addAll(GleamMapHelperDsl.mapHelpers(model, shape, sp, typesModule));
    }

    return Module.of(
        layout.codecModuleName(ctx.resolvedProtocolTraitId()),
        DocComment.of("REST JSON 1 codecs for " + service.getId() + " (generated). Do not edit."),
        List.of(
            Import.of(typesModule),
            Import.of("gleam/dict"),
            Import.of("gleam/dynamic"),
            Import.of("gleam/dynamic/decode"),
            Import.of("gleam/float"),
            Import.of("gleam/json"),
            Import.of("gleam/option"),
            Import.of("gleam/result"),
            Import.of("gleam/time/timestamp"),
            Import.of("bigdecimal")),
        List.of(),
        List.of(),
        functions);
  }
}
