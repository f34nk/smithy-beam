package io.smithy.beam.elixir;

import io.beam.dsl.elixir.Module;
import io.smithy.beam.core.BeamEdition;
import io.smithy.beam.core.BeamElixirLayout;
import io.smithy.beam.core.BeamEventStreamIndex;
import software.amazon.smithy.codegen.core.SymbolProvider;
import software.amazon.smithy.model.shapes.ServiceShape;
import software.amazon.smithy.model.shapes.UnionShape;

/** Emits Amazon Event Stream encode and decode helpers for {@code @streaming} union shapes. */
public final class ElixirEventStreamEmitter {

  private ElixirEventStreamEmitter() {}

  public static void emit(ElixirContext ctx, ServiceShape service) {
    if (!BeamEdition.fromSettings(ctx.settings()).supportsEventStreams()) {
      return;
    }
    BeamEventStreamIndex index = BeamEventStreamIndex.of(ctx.model());
    if (index.eventStreamUnions(service).isEmpty()) {
      return;
    }

    BeamElixirLayout layout =
        new BeamElixirLayout(ctx.settings(), service.getId().getNamespace(), service);
    Module module = ElixirEventStreamDsl.eventStreamModule(ctx, service);
    ElixirCodecEmission.writeModule(ctx, layout.eventStreamModuleFile(), module);
  }

  static String helperName(SymbolProvider sp, UnionShape union) {
    return ElixirEventStreamDsl.helperName(sp, union);
  }

  static String unionTagForMember(
      SymbolProvider sp, software.amazon.smithy.model.shapes.MemberShape member) {
    return ElixirUnionHelperDsl.unionTagForMember(sp, member);
  }
}
