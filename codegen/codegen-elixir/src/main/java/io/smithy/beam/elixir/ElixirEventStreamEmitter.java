package io.smithy.beam.elixir;

import io.smithy.beam.core.BeamEdition;
import io.smithy.beam.core.BeamElixirLayout;
import io.smithy.beam.core.BeamEventStreamIndex;
import io.smithy.beam.ir.elixir.ExModule;
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
    ExModule module = ElixirEventStreamIr.eventStreamModule(ctx, service);

    ctx.writerDelegator()
        .useFileWriter(
            layout.eventStreamModuleFile(), writer -> writer.write("$L", module.asString()));
  }

  static String helperName(SymbolProvider sp, UnionShape union) {
    return ElixirEventStreamIr.helperName(sp, union);
  }

  static String unionTagForMember(
      SymbolProvider sp, software.amazon.smithy.model.shapes.MemberShape member) {
    return ElixirUnionHelperIr.unionTagForMember(sp, member);
  }
}
